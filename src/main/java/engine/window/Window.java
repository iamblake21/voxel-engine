package engine.window;

import engine.core.Config;
import org.lwjgl.opengl.GL;
import org.lwjgl.glfw.GLFWVidMode;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Window management using GLFW
 */
public class Window {
    
    private final Config config;
    private long handle;
    private int width, height;
    
    public Window(Config config) {
        this.config = config;
        this.width = config.windowWidth;
        this.height = config.windowHeight;
    }
    
    /**
     * Create and initialize the window
     */
    public void create() {
        if (!glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW");
        }
        
        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        
        // Create window
        handle = glfwCreateWindow(
            width, 
            height, 
            config.windowTitle, 
            config.fullscreen ? glfwGetPrimaryMonitor() : NULL,
            NULL
        );
        
        if (handle == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }
        
        // Setup resize callback
        glfwSetFramebufferSizeCallback(handle, (window, w, h) -> {
            this.width = w;
            this.height = h;
        });
        
        // Center window
        if (!config.fullscreen) {
            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidMode != null) {
                glfwSetWindowPos(handle,
                    (vidMode.width() - width) / 2,
                    (vidMode.height() - height) / 2
                );
            }
        }
        
        // Make context current
        glfwMakeContextCurrent(handle);
        glfwSwapInterval(config.vsync ? 1 : 0);
        
        // Create GL capabilities
        GL.createCapabilities();
        
        // Show window
        glfwShowWindow(handle);
        
        System.out.println("Window created: " + width + "x" + height);
    }
    
    /**
     * Swap front and back buffers
     */
    public void swapBuffers() {
        glfwSwapBuffers(handle);
    }
    
    /**
     * Check if window should close
     */
    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }
    
    /**
     * Request window to close
     */
    public void close() {
        glfwSetWindowShouldClose(handle, true);
    }
    
    /**
     * Destroy the window
     */
    public void destroy() {
        glfwDestroyWindow(handle);
        glfwTerminate();
    }
    
    /**
     * Set cursor mode (normal, hidden, disabled)
     */
    public void setCursorMode(int mode) {
        glfwSetInputMode(handle, GLFW_CURSOR, mode);
    }
    
    /**
     * Get window aspect ratio
     */
    public float getAspectRatio() {
        return (float) width / height;
    }
    
    // Getters
    public long getHandle() { return handle; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}