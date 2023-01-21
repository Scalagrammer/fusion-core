package scg.fusion;

import scg.fusion.exceptions.IllegalContractException;

import static java.util.Arrays.asList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static scg.fusion.Utils.*;

public interface Environment {

    String getProperty(String property);


    default int getIntOrDefault(String property, int defaultValue) {

        String value = getProperty(property);

        return isNull(value) ? defaultValue : Integer.parseInt(value);

    }


    default char getChar(String property) {

        String value = getProperty(property);

        if (1 == value.length()) {
            return value.charAt(0);
        }

        throw new IllegalContractException("expected single character for property [%s] but actual is [%s]", property, value);

    }

    default boolean hasProperty(String property) {
        return nonNull(getProperty(property));
    }

    default int getByte(String property) {
        return Byte.parseByte(getProperty(property));
    }

    default int getInt(String property) {
        return Integer.parseInt(getProperty(property));
    }

    default long getLong(String property) {
        return Long.parseLong(getProperty(property));
    }

    default int getShort(String property) {
        return Short.parseShort(getProperty(property));
    }

    default float getFloat(String property) {
        return Float.parseFloat(getProperty(property));
    }

    default double getDouble(String property) {
        return Double.parseDouble(getProperty(property));
    }

    default boolean getBoolean(String property) {

        String value = getProperty(property);

        for (String keyWord : asList(ON, ENABLED, ENABLE)) {
            if (keyWord.equalsIgnoreCase(value)) {
                return true;
            }
        }

        for (String keyWord : asList(OFF, DISABLED, DISABLE)) {
            if (keyWord.equalsIgnoreCase(value)) {
                return true;
            }
        }

        return Boolean.parseBoolean(value);

    }

}
