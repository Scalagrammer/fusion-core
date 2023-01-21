package scg.fusion.cglib.core;

public interface GeneratorStrategy {

    byte[] generate(ClassGenerator cg) throws Exception;

    boolean equals(Object o);

}
