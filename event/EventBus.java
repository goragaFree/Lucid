package dev.lucid.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Synchronous bus. Game events must be posted on the Minecraft client thread.
 * Registration uses identity, is idempotent and publishes an immutable snapshot.
 * A subscription change takes effect on the NEXT post, not halfway through one.
 * Reflection is used only when registering; dispatch uses bound method handles.
 */
public final class EventBus {
    private volatile List<Handler> handlers = List.of();
    private final BiConsumer<Event, Throwable> errorHandler;

    public EventBus(BiConsumer<Event, Throwable> errorHandler) {
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    public synchronized void subscribe(Object listener) {
        Objects.requireNonNull(listener, "listener");
        if (handlers.stream().anyMatch(handler -> handler.owner == listener)) return;

        List<Handler> additions = new ArrayList<>();
        Set<String> overridden = new HashSet<>();
        for (Class<?> type = listener.getClass(); type != null; type = type.getSuperclass()) {
            Method[] methods = type.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::toGenericString));
            for (Method method : methods) {
                if (method.isSynthetic() || method.isBridge()) continue;
                // Private superclass methods are independent, not overridden.
                String signature = method.getName() + Arrays.toString(method.getParameterTypes());
                if (!Modifier.isPrivate(method.getModifiers()) && !overridden.add(signature)) continue;
                Subscribe annotation = method.getAnnotation(Subscribe.class);
                if (annotation == null) continue;
                if (Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())
                        || method.getReturnType() != void.class || method.getParameterCount() != 1
                        || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    throw new IllegalArgumentException("Invalid @Subscribe method: " + method);
                }
                try {
                    MethodHandle handle = MethodHandles.privateLookupIn(type, MethodHandles.lookup())
                            .unreflect(method).bindTo(listener);
                    additions.add(new Handler(listener, method.getParameterTypes()[0], handle,
                            annotation.priority(), annotation.receiveCancelled()));
                } catch (IllegalAccessException exception) {
                    throw new IllegalArgumentException("Cannot access event listener: " + method, exception);
                }
            }
        }
        List<Handler> next = new ArrayList<>(handlers);
        next.addAll(additions);
        // Stable sort: registration order breaks ties between listeners.
        next.sort(Comparator.comparing(Handler::priority));
        handlers = List.copyOf(next);
    }

    public synchronized void unsubscribe(Object listener) {
        Objects.requireNonNull(listener, "listener");
        handlers = handlers.stream().filter(handler -> handler.owner != listener).toList();
    }

    /** Listeners for Event or another base class also receive derived events. */
    public <T extends Event> T post(T event) {
        Objects.requireNonNull(event, "event");
        for (Handler handler : handlers) {
            if (!handler.type.isInstance(event)) continue;
            if (event instanceof CancellableEvent cancellable
                    && cancellable.isCancelled() && !handler.receiveCancelled) continue;
            try {
                handler.handle.invoke(event);
            } catch (VirtualMachineError | LinkageError fatal) {
                throw fatal;
            } catch (Throwable failure) {
                errorHandler.accept(event, failure);
            }
        }
        return event;
    }

    private record Handler(Object owner, Class<?> type, MethodHandle handle,
                           Priority priority, boolean receiveCancelled) {}
}
