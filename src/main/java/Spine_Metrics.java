import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.*;
import ij.plugin.filter.Convolver;
import ij.plugin.frame.PlugInFrame;
import ij.process.ImageProcessor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;

public class Spine_Metrics extends PlugInFrame implements MouseListener {

    private ImageCanvas canvas;
    private static Frame instance;
    private ImagePlus img;
    private ImageWindow win;
    private ImageProcessor imageProcessor;;
    private int baseLineOrientation; /**Horizontal = 0, Vertival = 1 */
    private static final Point nullPoint = new Point(-1, -1);
    private Point p_l, p_r, p_c;
    private int prevImgHash;

    public Spine_Metrics() {
        super("Testing");
        instance = this;

        try {
            img = WindowManager.getCurrentImage();
            ImagePlus img2 = img.duplicate();
            win = img.getWindow();
            canvas = win.getCanvas();
            imageProcessor = img.getProcessor();
            imageProcessor.setColor(128);
            Convolver cv = new Convolver();
            cv.setNormalize(false);
            float[] K = { -0.125f, -0.125f, -0.125f,
                          -0.125f,    1.0f,    -0.125f,
                          -0.125f, -0.125f, -0.125f };
            cv.convolve(imageProcessor, K, 3, 3);
            IJ.setThreshold(0, 255, "Over/Under");
            int H = img.getHeight();
            int W  = img.getWidth();
            for (int i = 0; i < H; i++) {
                for (int j = 0; j < W; j++) {
                    if (imageProcessor.get(i,j) > 0)
                        imageProcessor.set(i,j,255);
                }
            }

            //IJ.run(img2, "Skeletonize (2D/3D)", "");
            //imageProcessor.copyBits(img2.getProcessor(), 0, 0, Blitter.MAX);

            canvas.imageUpdate(img.getImage(), 32, 0, 0, W, H);
            prevImgHash = img.hashCode();
            p_l = p_c = p_r = nullPoint;
            canvas.addMouseListener(this);
        } catch (Exception e) {
            IJ.showMessage("Error", e.getStackTrace().toString());
        }
    }

    //PointToolOptions

    public Point getP_l() {
        return p_l;
    }

    public Point getP_c() {
        return p_c;
    }

    public Point getP_r() {
        return p_r;
    }

    /**
     * @param e - basically MouseClicked event
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        imageProcessor.setColor(128);
        Point p = nullPoint;
        int x = canvas.offScreenX(e.getX());
        int y = canvas.offScreenY(e.getY());
        img = canvas.getImage();

        p = new Point(x,y);
        if (verifyPoint(img, p)) {
            imageProcessor.drawDot(p.x, p.y);
            if (p_l.equals(nullPoint))
                p_l = p;
            else {
                baseLineOrientation = 1;
                if (isBaseLineHorizontal(p_l.x, p_l.y, p.x, p.y)) {
                    baseLineOrientation = 0;
                    if (p.x < p_l.x) {
                        p_r = p_l;
                        p_l = p;
                    } else
                        p_r = p;
                } else if (p.y < p_l.y) {
                    p_r = p_l;
                    p_l = p;
                } else
                    p_r = p;
                process2(img);
            }
        } else {
            IJ.showMessage("Wrong Input", "Can't choose background pixels");
        }
    }


    boolean isBaseLineHorizontal(int x0, int y0, int x1, int y1) {
        if (y0 == y1) return true;
        return (Math.abs(x1-x0)/Math.abs(y1-y0) >= 1);
    }


    void process2(ImagePlus img) {
        Point nextPoint = p_l;
        ArrayList<Point> edge = new ArrayList<>();
        ArrayList<Point> resEdge = new ArrayList<>();
        edge.add(new Point(p_l));
        boolean isMovingUp = false;
        boolean isMovingLeft = false;
        if (baseLineOrientation == 0) {

            while (verifyPoint(img, nextPoint.x+1, nextPoint.y)) {
                nextPoint.x += 1;
                edge.add(new Point(nextPoint));
            }

            if (verifyPoint(img, nextPoint.x, nextPoint.y-1)) {
                nextPoint.y -= 1;
                isMovingUp = true;
                edge.add(new Point(nextPoint));
            } else if (verifyPoint(img, nextPoint.x, nextPoint.y+1)) {
                nextPoint.y+=1;
                edge.add(new Point(nextPoint));
            } else {
                IJ.showMessage("Error", "Unable to recognize srtucture");
                return;
            }
            resEdge = parseEdge(img, nextPoint, edge, isMovingUp, isMovingLeft);
        } else {
            while (verifyPoint(img, nextPoint.x, nextPoint.y+1)) {
                nextPoint.y+=1;
                edge.add(new Point(nextPoint));
            }

            if (verifyPoint(img, nextPoint.x-1, nextPoint.y)) {
                nextPoint.x-=1;
                isMovingLeft = true;
                edge.add(new Point(nextPoint));
            } else if (verifyPoint(img, nextPoint.x+1, nextPoint.y)) {
                nextPoint.x+=1;
                edge.add(new Point(nextPoint));
            } else {
                IJ.showMessage("Error", "Unable to recognize srtucture");
                return;
            }
            resEdge = parseEdge(img, nextPoint, edge, isMovingUp, isMovingLeft);
        }

        int[] xp = new int[resEdge.size()/2 + 1];
        int[] yp = new int[resEdge.size()/2 + 1];
        int nPoints = 0;
        LineSegment maxLine = new LineSegment(edge.get(0).x, edge.get(0).y, edge.get(edge.size()-1).x, edge.get(edge.size()-1).y);
        boolean isNewMax = false;
        for (int i = 0; i < resEdge.size()/2; i++) {
            LineSegment line = new LineSegment(resEdge.get(i).x, resEdge.get(i).y, resEdge.get(resEdge.size()-i-1).x, resEdge.get(resEdge.size()-i-1).y);
            if (line.getLength() > maxLine.getLength()+2) {
                maxLine = line;
                isNewMax = true;
            }
            xp[i] = (int)line.midPoint().x;
            yp[i] = (int)line.midPoint().y;
            nPoints++;
        }
        PolygonRoi skeleton = new PolygonRoi(xp, yp, nPoints, Roi.POLYLINE);
        PolygonRoi maxPolyLine = new PolygonRoi(new int[]{(int)maxLine.p0.x, (int)maxLine.p1.x}, new int[]{(int)maxLine.p0.y, (int)maxLine.p1.y}, 2, Roi.POLYLINE);
        imageProcessor.drawRoi(skeleton);
        imageProcessor.drawRoi(maxPolyLine);

        String string = "";
        for (Point p : resEdge) {
            imageProcessor.drawDot(p.x, p.y);
        }

        p_l = p_r = nullPoint;

        if (isNewMax)
            string = "Shroomie spine";
        else
            string = "Penek spine";
        IJ.showMessage("Result", string);
    }

    private ArrayList<Point> parseEdge(ImagePlus img, Point nextPoint, ArrayList<Point> edge, boolean isMovingUp, boolean isMovingLeft) {
        while (!nextPoint.equals(p_r)) {

            /**LEFT*/
            if (verifyPoint(img, nextPoint.x-1, nextPoint.y)
                    && !isPrevious(edge.get(edge.size()-2), nextPoint.x-1, nextPoint.y)
                    && !isSingle4Neighboured(nextPoint.x-1, nextPoint.y)) {
                if (verifyPoint(img, nextPoint.x+1, nextPoint.y) && !isMovingLeft)
                    nextPoint.x += 1;
                else {
                    nextPoint.x -= 1;
                    isMovingLeft = true;
                }
                edge.add(new Point(nextPoint));
            }

            /**UP*/
            else if (verifyPoint(img, nextPoint.x, nextPoint.y-1)
                    && !isPrevious(edge.get(edge.size()-2), nextPoint.x, nextPoint.y-1)
                    && !isSingle4Neighboured(nextPoint.x, nextPoint.y-1)) {
                if (verifyPoint(img, nextPoint.x, nextPoint.y+1) && !isMovingUp )
                    nextPoint.y += 1;
                else {
                    nextPoint.y -= 1;
                    isMovingUp = true;
                }
                edge.add(new Point(nextPoint));
            }

            /**RIGHT*/
            else if (verifyPoint(img, nextPoint.x+1, nextPoint.y)
                    && !isPrevious(edge.get(edge.size()-2), nextPoint.x+1, nextPoint.y)
                    && !isSingle4Neighboured(nextPoint.x+1, nextPoint.y)) {
                nextPoint.x += 1;
                isMovingLeft = false;
                edge.add(new Point(nextPoint));
            }

            /**DOWN*/
            else if (verifyPoint(img, nextPoint.x, nextPoint.y+1)
                    && !isPrevious(edge.get(edge.size()-2), nextPoint.x, nextPoint.y+1)
                    && !isSingle4Neighboured(nextPoint.x, nextPoint.y+1)) {
                isMovingUp=false;
                nextPoint.y += 1;
                edge.add(new Point(nextPoint));
            }
            else
                break;
        }
        return edge;
    }

    boolean isPrevious(Point previous, int x, int y) {
        return (previous.x == x && previous.y == y);
    }

    boolean isSingle4Neighboured(int x, int y) {
        int count = 0;
        if (verifyPoint(img,x-1,y))
            count++;
        if (verifyPoint(img,x+1,y))
            count++;
        if (verifyPoint(img,x,y-1))
            count++;
        if (verifyPoint(img,x,y+1))
            count++;
        return count <= 1;
    }


    public boolean verifyPoint(ImagePlus imp, Point point) {
        return imageProcessor.getPixel(point.x, point.y) > 0;
    }

    public boolean verifyPoint(ImagePlus imp, int x, int y) {
        return imageProcessor.getPixel(x, y) > 0;
    }

    public Point autoCorrectPoint(ImagePlus imp, Point point) {
        int x = 6, y = 6;
        double distance = 6, tempDistance;
        Coordinate initPoint = new Coordinate(point.x, point.y);

        for (int i = 0; i <= 4; i++) {
            for (int j = 0; j <= 4 ; j++) {
                if ( verifyPoint(imp, new Point(point.x - 2 + i, point.y - 2 + j)) ) {
                    tempDistance = initPoint.distance(new Coordinate(point.x - 2 + i, point.y - 2 + j));
                    if ( tempDistance < distance ) {
                        distance = tempDistance;
                        x = i - 2;
                        y = j - 2;
                    }
                }
            }
        }
        if ( x != 6 ) {
            if ( y != 6 )
                return new Point(point.x + x, point.y + y);
            else
                return new Point(point.x + x, point.y);
        } else {
            if (y != 6)
                return new Point(point.x, point.y + y);
            else
                return nullPoint;
        }
    }


    @Override
    public void mousePressed(MouseEvent e) {}

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}
}
