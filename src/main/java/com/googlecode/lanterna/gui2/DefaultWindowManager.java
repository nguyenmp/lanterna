package com.googlecode.lanterna.gui2;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;

import java.util.List;

/**
 * The default window manager implementation used by Lanterna. New windows will be generally added in a tiled manner,
 * starting in the top-left corner and moving down-right as new windows are added. By using the various window hints
 * that are available you have some control over how the window manager will place and size the windows.
 *
 * @author Martin
 */
public class DefaultWindowManager implements WindowManager {

    private final WindowDecorationRenderer windowDecorationRenderer;
    private TerminalSize lastKnownScreenSize;

    /**
     * Default constructor, will create a window manager that uses {@code DefaultWindowDecorationRenderer} for drawing
     * window decorations. Any size calculations done before the text GUI has actually been started and displayed on
     * the terminal will assume the terminal size is 80x24.
     */
    public DefaultWindowManager() {
        this(new DefaultWindowDecorationRenderer());
    }

    /**
     * Creates a new {@code DefaultWindowManager} with a specific window decoration renderer. Any size calculations done
     * before the text GUI has actually been started and displayed on the terminal will assume the terminal size is
     * 80x24.
     *
     * @param windowDecorationRenderer Window decoration renderer to use when drawing windows
     */
    public DefaultWindowManager(WindowDecorationRenderer windowDecorationRenderer) {
        this(windowDecorationRenderer, null);
    }

    /**
     * Creates a new {@code DefaultWindowManager} using a {@code DefaultWindowDecorationRenderer} for drawing window
     * decorations. Any size calculations done before the text GUI has actually been started and displayed on the
     * terminal will use the size passed in with the {@code initialScreenSize} parameter
     *
     * @param initialScreenSize Size to assume the terminal has until the text GUI is started and can be notified of the
     *                          correct size
     */
    public DefaultWindowManager(TerminalSize initialScreenSize) {
        this(new DefaultWindowDecorationRenderer(), initialScreenSize);
    }

    /**
     * Creates a new {@code DefaultWindowManager} using a specified {@code windowDecorationRenderer} for drawing window
     * decorations. Any size calculations done before the text GUI has actually been started and displayed on the
     * terminal will use the size passed in with the {@code initialScreenSize} parameter
     *
     * @param windowDecorationRenderer Window decoration renderer to use when drawing windows
     * @param initialScreenSize Size to assume the terminal has until the text GUI is started and can be notified of the
     *                          correct size
     */
    public DefaultWindowManager(WindowDecorationRenderer windowDecorationRenderer, TerminalSize initialScreenSize) {
        this.windowDecorationRenderer = windowDecorationRenderer;
        if(initialScreenSize != null) {
            this.lastKnownScreenSize = initialScreenSize;
        }
        else {
            this.lastKnownScreenSize = new TerminalSize(80, 24);
        }
    }

    @Override
    public boolean isInvalid() {
        return false;
    }

    @Override
    public WindowDecorationRenderer getWindowDecorationRenderer(Window window) {
        if(window.getHints().contains(Window.Hint.NO_DECORATIONS)) {
            return new EmptyWindowDecorationRenderer();
        }
        return windowDecorationRenderer;
    }

    @Override
    public void onAdded(WindowBasedTextGUI textGUI, Window window, List<Window> allWindows) {
        WindowDecorationRenderer decorationRenderer = getWindowDecorationRenderer(window);
        TerminalSize expectedDecoratedSize = decorationRenderer.getDecoratedSize(window, window.getPreferredSize());
        window.setDecoratedSize(expectedDecoratedSize);

        if(window.getHints().contains(Window.Hint.FIXED_POSITION)) {
            //Don't place the window, assume the position is already set
        }
        else if(allWindows.isEmpty()) {
            window.setPosition(TerminalPosition.OFFSET_1x1);
        }
        else if(window.getHints().contains(Window.Hint.CENTERED)) {
            int left = (lastKnownScreenSize.getColumns() - expectedDecoratedSize.getColumns()) / 2;
            int top = (lastKnownScreenSize.getRows() - expectedDecoratedSize.getRows()) / 2;
            window.setPosition(new TerminalPosition(left, top));
        }
        else {
            TerminalPosition nextPosition = allWindows.get(allWindows.size() - 1).getPosition().withRelative(2, 1);
            if(nextPosition.getColumn() + expectedDecoratedSize.getColumns() > lastKnownScreenSize.getColumns() ||
                    nextPosition.getRow() + expectedDecoratedSize.getRows() > lastKnownScreenSize.getRows()) {
                nextPosition = TerminalPosition.OFFSET_1x1;
            }
            window.setPosition(nextPosition);
        }

        // Finally, run through the usual calculations so the window manager's usual prepare method can have it's say
        prepareWindow(lastKnownScreenSize, window);
    }

    @Override
    public void onRemoved(WindowBasedTextGUI textGUI, Window window, List<Window> allWindows) {
        //NOP
    }

    @Override
    public void prepareWindows(WindowBasedTextGUI textGUI, List<Window> allWindows, TerminalSize screenSize) {
        this.lastKnownScreenSize = screenSize;
        for(Window window: allWindows) {
            prepareWindow(screenSize, window);
        }
    }

    /**
     * Called by {@link DefaultWindowManager} when iterating through all windows to decide their size and position. If
     * you override {@link DefaultWindowManager} to add your own logic to how windows are placed on the screen, you can
     * override this method and selectively choose which window to interfere with. Note that the two key properties that
     * are read by the GUI system after preparing all windows are the position and decorated size. Your custom
     * implementation should set these two fields directly on the window. You can infer the decorated size from the
     * content size by using the window decoration renderer that is attached to the window manager.
     *
     * @param screenSize Size of the terminal that is available to draw on
     * @param window Window to prepare decorated size and position for
     */
    protected void prepareWindow(TerminalSize screenSize, Window window) {
        WindowDecorationRenderer decorationRenderer = getWindowDecorationRenderer(window);
        TerminalSize contentAreaSize;
        if(window.getHints().contains(Window.Hint.FIXED_SIZE)) {
            contentAreaSize = window.getSize();
        }
        else {
            contentAreaSize = window.getPreferredSize();
        }
        TerminalSize size = decorationRenderer.getDecoratedSize(window, contentAreaSize);
        TerminalPosition position = window.getPosition();

        if(window.getHints().contains(Window.Hint.FULL_SCREEN)) {
            position = TerminalPosition.TOP_LEFT_CORNER;
            size = screenSize;
        }
        else if(window.getHints().contains(Window.Hint.EXPANDED)) {
            position = TerminalPosition.OFFSET_1x1;
            size = screenSize.withRelative(
                    -Math.min(4, screenSize.getColumns()),
                    -Math.min(3, screenSize.getRows()));
            if(!size.equals(window.getDecoratedSize())) {
                window.invalidate();
            }
        }
        else if(window.getHints().contains(Window.Hint.FIT_TERMINAL_WINDOW) ||
                window.getHints().contains(Window.Hint.CENTERED)) {
            //If the window is too big for the terminal, move it up towards 0x0 and if that's not enough then shrink
            //it instead
            while(position.getRow() > 0 && position.getRow() + size.getRows() > screenSize.getRows()) {
                position = position.withRelativeRow(-1);
            }
            while(position.getColumn() > 0 && position.getColumn() + size.getColumns() > screenSize.getColumns()) {
                position = position.withRelativeColumn(-1);
            }
            if(position.getRow() + size.getRows() > screenSize.getRows()) {
                size = size.withRows(screenSize.getRows() - position.getRow());
            }
            if(position.getColumn() + size.getColumns() > screenSize.getColumns()) {
                size = size.withColumns(screenSize.getColumns() - position.getColumn());
            }
            if(window.getHints().contains(Window.Hint.CENTERED)) {
                int left = (lastKnownScreenSize.getColumns() - size.getColumns()) / 2;
                int top = (lastKnownScreenSize.getRows() - size.getRows()) / 2;
                position = new TerminalPosition(left, top);
            }
        }

        window.setPosition(position);
        window.setDecoratedSize(size);
    }
}
