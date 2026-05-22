package presentation;

/**
 * Punto de entrada del programa. Lanza la interfaz Swing
 * ({@link VentanaMenuPrincipal}). Si el sistema es headless o se pasa el
 * flag {@code --consola}, cae al menú por consola tradicional
 * ({@link ConsolaMenu}).
 *
 * Ejecutar en NetBeans: Run Project (F6)
 * Ejecutar con Maven:   mvn exec:java
 */
public class Main {

    public static void main(String[] args) {
        boolean forzarConsola = false;
        for (String a : args) {
            if ("--consola".equalsIgnoreCase(a) || "-c".equalsIgnoreCase(a)) {
                forzarConsola = true;
                break;
            }
        }
        if (forzarConsola || java.awt.GraphicsEnvironment.isHeadless()) {
            new ConsolaMenu().iniciar();
        } else {
            instalarLookAndFeel();
            VentanaMenuPrincipal.mostrar();
        }
    }

    /** Activa FlatDarkLaf antes de instanciar cualquier componente Swing. */
    private static void instalarLookAndFeel() {
        try {
            com.formdev.flatlaf.FlatDarkLaf.setup();
            javax.swing.UIManager.put("Button.arc", 12);
            javax.swing.UIManager.put("Component.arc", 12);
            javax.swing.UIManager.put("ProgressBar.arc", 12);
            javax.swing.UIManager.put("TextComponent.arc", 8);
            javax.swing.UIManager.put("ScrollBar.thumbArc", 999);
            javax.swing.UIManager.put("ScrollBar.thumbInsets",
                    new java.awt.Insets(2, 2, 2, 2));
        } catch (Exception ignored) { /* fallback al L&F por defecto */ }
    }
}
