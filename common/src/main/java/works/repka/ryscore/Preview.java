package works.repka.ryscore;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Предварительный контракт: может поменяться до выпуска 1.0 мода, который его реализует (RYSMap, RYSClaims).
 * Правило «в 1.x только добавляем» начинает действовать для него с первой сборки ядра, в которой у контракта три
 * потребителя; до того несовместимая правка записывается в CHANGELOG ядра.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface Preview {
    /** Мод, после выпуска 1.0 которого контракт перестаёт быть предварительным. */
    String until() default "";
}
