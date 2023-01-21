package scg.fusion;

import com.sun.org.apache.bcel.internal.generic.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.annotation.Annotation;

public final class MethodJoint implements Joint {

    private final Method joint;

    MethodJoint(Method joint) {
        this.joint = joint;
    }

    public boolean isDefault() {
        return joint.isDefault();
    }

    public String getSignature() {
        return Type.getSignature(joint);
    }

    public Class<?> getReturnType() {
        return joint.getReturnType();
    }

    public int getParameterCount() {
        return joint.getParameterCount();
    }

    public Parameter[] getParameters() {
        return joint.getParameters();
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
    public Annotation[] getAnnotations() {
        return joint.getAnnotations();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return joint.getAnnotation(annotationType);
    }

    @Override
    public Class<?> getComponentType() {
        return joint.getDeclaringClass();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return joint.getDeclaredAnnotations();
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

        if (obj instanceof MethodJoint) {
            return ((MethodJoint) obj).joint.equals(joint);
        }

        return false;

    }

    @Override
    public String toString() {
        return joint.toString();
    }
}
