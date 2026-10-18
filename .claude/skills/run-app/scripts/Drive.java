import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Drives the app on the private display with java.awt.Robot. Input works there; screenshots do not (see shot.py).
 * Usage: DISPLAY=:97 java Drive.java click X Y | type TEXT | key NAME | clear | wait MS ...
 * Coordinates are screen pixels, as in a full screenshot. key takes a KeyEvent name without VK_, e.g. ESCAPE, ENTER.
 */
public class Drive {
    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(60);
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "click" -> {
                    robot.mouseMove(Integer.parseInt(args[++i]), Integer.parseInt(args[++i]));
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                }
                case "type" -> {
                    for (char c : args[++i].toCharArray()) {
                        int code = KeyEvent.getExtendedKeyCodeForChar(c);
                        boolean shift = Character.isUpperCase(c);
                        if (shift) robot.keyPress(KeyEvent.VK_SHIFT);
                        robot.keyPress(code);
                        robot.keyRelease(code);
                        if (shift) robot.keyRelease(KeyEvent.VK_SHIFT);
                    }
                }
                case "key" -> {
                    int code = KeyEvent.class.getField("VK_" + args[++i]).getInt(null);
                    robot.keyPress(code);
                    robot.keyRelease(code);
                }
                case "clear" -> {
                    robot.keyPress(KeyEvent.VK_CONTROL);
                    robot.keyPress(KeyEvent.VK_A);
                    robot.keyRelease(KeyEvent.VK_A);
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                    robot.keyPress(KeyEvent.VK_BACK_SPACE);
                    robot.keyRelease(KeyEvent.VK_BACK_SPACE);
                }
                case "wait" -> Thread.sleep(Long.parseLong(args[++i]));
                default -> throw new IllegalArgumentException("Unknown command: " + args[i]);
            }
        }
    }
}
