package com.fivedoorsescape.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.fivedoorsescape.FiveDoorsEscapeGame;
import com.fivedoorsescape.util.EstadoVentana;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.windows.INPUT;
import org.lwjgl.system.windows.User32;

public class Lwjgl3Launcher {

    private static Lwjgl3Window ventana;

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("five_doors_escape");
        // Pantalla completa segun el flujo final pretendido (ver memoria de Claude
        // "project-libgdx-office-spawn-exit-design"): el Escape reemplaza visualmente a la
        // ventana de Swing, sin bordes de ventana de por medio.
        config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        config.useVsync(true);
        config.setForegroundFPS(60);
        // Foco (CLAUDE.md §2.71): GLFW avisa cada cambio y core decide (activacion inicial, pausa al perder el foco)
        config.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public void created(Lwjgl3Window window) {
                ventana = window;
            }

            @Override
            public void focusGained() {
                EstadoVentana.alCambiarFoco(true);
            }

            @Override
            public void focusLost() {
                EstadoVentana.alCambiarFoco(false);
            }
        });
        EstadoVentana.configurar(Lwjgl3Launcher::pedirFoco);
        new Lwjgl3Application(new FiveDoorsEscapeGame(), config);
    }

    /**
     * Trae la ventana al frente con el teclado (CLAUDE.md §2.71). glfwFocusWindow llama a SetForegroundWindow, que
     * Windows rechaza si el proceso no es el que esta en primer plano (FDAF lanza Escape a traves de cmd y el .bat, asi
     * que Escape nunca lo es). Si el primer intento no alcanza, se usa la tecnica habitual en Windows: una pulsacion
     * sintetica de ALT (SendInput) libera el bloqueo de primer plano y el segundo intento ya se acepta. FDAF queda
     * debajo sin minimizarse, y al cerrar Escape vuelve a ser la ventana activa como antes.
     */
    private static void pedirFoco() {
        if (ventana == null) {
            return;
        }
        long h = ventana.getWindowHandle();
        String via = "ya lo tenia";
        if (GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE) {
            ventana.focusWindow();
            via = "glfwFocusWindow";
        }
        if (GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE
                && System.getProperty("os.name", "").toLowerCase().contains("win")) {
            pulsarAlt();
            ventana.focusWindow();
            via = "ALT + glfwFocusWindow";
        }
        boolean enfocada = GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        System.out.println("[Ventana] foco pedido (" + via + "): " + (enfocada ? "concedido" : "pendiente"));
        if (enfocada) {
            EstadoVentana.alCambiarFoco(true);
        }
    }

    private static void pulsarAlt() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            INPUT.Buffer entradas = INPUT.calloc(2, stack);
            entradas.get(0).type(User32.INPUT_KEYBOARD).DUMMYUNIONNAME_ki().wVk((short) User32.VK_MENU).dwFlags(0);
            entradas.get(1).type(User32.INPUT_KEYBOARD).DUMMYUNIONNAME_ki().wVk((short) User32.VK_MENU)
                    .dwFlags(User32.KEYEVENTF_KEYUP);
            User32.SendInput(entradas, INPUT.SIZEOF);
        } catch (Throwable t) {
            System.out.println("[Ventana] no se pudo enviar ALT: " + t);
        }
    }
}
