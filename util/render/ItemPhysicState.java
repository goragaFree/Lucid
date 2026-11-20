package dev.lucid.util.render;

/**
 * Duck-тип для состояния рендера упавшего предмета, реализацию добавляет
 * {@code ItemEntityRenderStateMixin}.
 *
 * <p>ItemPhysic считает спин при извлечении состояния ({@code extractRenderState}),
 * а читает его уже при отправке ({@code submit}) — между этими фазами сущность
 * недоступна, а сам {@code ItemEntityRenderState} переиспользуется между предметами,
 * поэтому везти угол нужно на состоянии, а не в поле модуля.</p>
 *
 * <p>Интерфейс живёт вне {@code dev.lucid.mixin}: классы из mixin-пакета запрещено
 * грузить напрямую из обычного кода, а каст лежит именно в {@code ItemPhysic}.</p>
 */
public interface ItemPhysicState {

    void lucid$setSpinYaw(float yaw);

    float lucid$spinYaw();
}
