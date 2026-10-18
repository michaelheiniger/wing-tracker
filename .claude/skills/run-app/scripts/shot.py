"""Screenshots the private display. java.awt.Robot captures only black there, so PIL grabs it instead.

Usage: python3 -I shot.py <display> <out.png> [left top right bottom]
"""
import sys

from PIL import ImageGrab

display, out = sys.argv[1], sys.argv[2]
image = ImageGrab.grab(xdisplay=display)
if len(sys.argv) == 7:
    image = image.crop(tuple(int(v) for v in sys.argv[3:7]))
image.save(out)
print(out)
