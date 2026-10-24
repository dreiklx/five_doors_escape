package com.fivedoorsescape.util;

/**
 * Foco de la ventana de Escape (CLAUDE.md §2.71). Escape se abre en otro proceso y otra ventana que FDAF; Windows no
 * deja que un proceso en segundo plano se quede con el teclado, asi que la ventana podia aparecer encima pero sin
 * responder hasta hacer clic. FDAF no se toca: si se minimizara para ceder el primer plano, al cerrar Escape Windows
 * no se lo devolveria (salta las ventanas minimizadas) y el regreso quedaria sin foco. core no conoce el backend: el
 * lanzador LWJGL3 informa los cambios de foco (alCambiarFoco) y aporta como pedirlo (configurar). Sin lanzador
 * (arneses, IDE sin backend) la ventana se da por enfocada y nada cambia.
 */
public final class EstadoVentana {

    private static volatile boolean enfocada = true;
    private static volatile boolean enfocadaAlgunaVez = true;
    private static Runnable solicitudFoco;

    private EstadoVentana() {
    }

    /** Lo llama el lanzador real: a partir de aqui el foco se informa con alCambiarFoco. */
    public static void configurar(Runnable pedirFoco) {
        solicitudFoco = pedirFoco;
        enfocada = false;
        enfocadaAlgunaVez = false;
    }

    public static void alCambiarFoco(boolean tieneFoco) {
        enfocada = tieneFoco;
        if (tieneFoco) {
            enfocadaAlgunaVez = true;
        }
    }

    public static boolean isEnfocada() {
        return enfocada;
    }

    /** False solo mientras la ventana nunca tuvo el teclado: entonces se muestra "Haz clic para entrar". */
    public static boolean isEnfocadaAlgunaVez() {
        return enfocadaAlgunaVez;
    }

    /** Pide el foco al sistema (glfwFocusWindow). Puede no concederse si otro proceso esta en primer plano. */
    public static void pedirFoco() {
        if (solicitudFoco != null) {
            solicitudFoco.run();
        }
    }
}
