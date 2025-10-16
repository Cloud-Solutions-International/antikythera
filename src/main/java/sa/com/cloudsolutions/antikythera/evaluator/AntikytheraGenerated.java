package sa.com.cloudsolutions.antikythera.evaluator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation applied to dynamically generated classes/methods by Antikythera.
 *
 * Java agents (e.g., Byte Buddy transformers) can use this marker to skip
 * instrumenting these elements to avoid verifier issues when combining
 * multiple layers of interception.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface AntikytheraGenerated {
}
