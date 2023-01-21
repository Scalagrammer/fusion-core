package scg.fusion;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

public final class FieldJoint implements Joint {

    private final Field joint;

    FieldJoint(Field joint) {
        this.joint = joint;
    }

    @Override
    public String getName() {
        return joint.getName();
    }

    @Override
    public int getModifiers() {
        return joint.getModifiers();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return joint.getAnnotation(annotationType);
    }

    @Override
    public Annotation[] getAnnotations() {
        return joint.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return joint.getDeclaredAnnotations();
    }

    @Override
    public Class<?> getComponentType() {
        return joint.getDeclaringClass();
    }

    @Override
    public int hashCode() {
        return joint.hashCode();
    }

    @Override
    public boolean equals(Object obj) {

        if (obj == this) {
            return true;
        }

        if (obj instanceof FieldJoint) {
            return ((FieldJoint) obj).joint.equals(joint);
        }

        return false;

    }

    @Override
    public String toString() {
        return joint.toString();
    }

}
