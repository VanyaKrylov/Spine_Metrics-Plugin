import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.plugin.filter.Filters;
import ij.plugin.frame.PlugInFrame;
import ij.process.ImageProcessor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Optional;

public class Spine_Metrics extends PlugInFrame implements MouseListener {

    private ImageCanvas canvas;
    private static Frame instance;
    private ImagePlus img;
    private ImageWindow win;
    private ImageProcessor imageProcessor;
    private boolean toFindEdges;

    private static final Point nullPoint = new Point(-1, -1);

    private int maxDistanceToBaselineEnd = 5;

    private Point p_l, p_r, p_c;
    //private Optional<Point> p_l, p_r, p_c;
    private int prevImgHash;

    public Spine_Metrics() {
        super("Testing");
        instance = this;

        try {
            img = WindowManager.getCurrentImage();
            win = img.getWindow();
            canvas = win.getCanvas();
            imageProcessor = img.getProcessor();
            imageProcessor.setColor(Color.red);
            prevImgHash = img.hashCode();
            toFindEdges = true;
            p_l = p_c = p_r = nullPoint;
            //p_l = p_c = p_r = Optional.empty();
            canvas.addMouseListener(this);
        } catch (Exception e) {
            IJ.showMessage("Error", e.getStackTrace().toString());
        }
    }


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
     * Need improve, the current logic works only if you follow the order of clicking:
     * 1) left point
     * 2) right point
     * 3) point inside the spine
     * @param e - basically MouseClicked event
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        Point p = nullPoint;
        int x = canvas.offScreenX(e.getX());
        int y = canvas.offScreenY(e.getY());
        img = canvas.getImage();
        if ( toFindEdges ) {
            toFindEdges = false;
            IJ.run(img, "Find Edges", "");
            IJ.showMessage("The initial image was converted to edges only");
            e.consume();
        } else {
            //img = WindowManager.getCurrentImage();

        /*if (prevImgHash != img.hashCode()) {
            prevImgHash = img.hashCode();
            p_l = nullPoint;
            p_c = nullPoint;
            p_r = nullPoint;

        }*/

            p = (verifyPoint(img, new Point(x, y))) ?
                    new Point(x, y) :
                    autoCorrectPoint(img, new Point(x, y));

            if (!p.equals(nullPoint)) {
                if (p_l.equals(nullPoint)) {
                    p_l = p;
                    imageProcessor.drawDot(p_l.x, p_l.y);
                } else if (p_r.equals(nullPoint)) {
                    p_r = p;
                    if (!verifyBasePoints(img, p_l, p_r)) {
                        IJ.showMessage("Wrong Input", "Error, the length of the base line is too big\n Discarding previous input");
                        p_l = nullPoint;
                        p_r = nullPoint;
                    } else {
                        imageProcessor.drawLine(p_l.x, p_l.y, p_r.x, p_r.y);
                        img.updateImage();
                    }
                }  else if (p_c.equals(nullPoint)) {
                    p_c = p;
                    imageProcessor.drawDot(p_c.x, p_c.y);
                    //IJ.showMessage(" p_l = " + p_l.x + " " + p_l.y + " p_r = " + p_r.x + " " + p_r.y + " p_c = " + p_c.x + " " + p_c.y);
                    process(img);

                    p_l = nullPoint;
                    p_c = nullPoint;
                    p_r = nullPoint;
                } else {
                    e.consume();
                    //process(img);
                    //IJ.showMessage(" p_l = " + p_l.toString() + " p_r = " + p_r.toString() + " p_c = " + p_c.toString());
                }
            } else
                IJ.showMessage("Wrong Input", "Can't choose background pixels");
        }
    }



    //public for testing
    /**
     * Spine metrics processing
     * @param imp
     * @return
     */
    public int process(ImagePlus imp) {

        Coordinate baseLeftBoundary = new Coordinate(p_l.x, p_l.y),baseRightBoundary = new Coordinate(p_r.x, p_r.y);
        LineSegment baseLine = new LineSegment(baseLeftBoundary, baseRightBoundary);
        Coordinate spineCenter = new Coordinate(p_c.x, p_c.y);
        Coordinate perpendPointProj = baseLine.closestPoint(spineCenter);

        imageProcessor.drawLine((int)perpendPointProj.x, (int)perpendPointProj.y, (int)spineCenter.x, (int)spineCenter.y);

        int offset = ( perpendPointProj.y < spineCenter.y ) ? 1 : -1;

/*
        Coordinate midPoint = baseLine.midPoint();
        LineSegment spineLine = baseLine;
        while ( verifyCoordinate(imp, midPoint = spineLine.pointAlongOffset(spineLine.getLength()/2, offset)) ) {
            while (  )
        } */

        LineSegment perpendicularToBase = new LineSegment(spineCenter, perpendPointProj);
        Coordinate startLeft = baseLine.pointAlongOffset(0, offset);
        Coordinate endLeft = baseLine.pointAlongOffset(1, offset);
        //LineString spineSkeletonLine = new GeometryFactory().createLineString(new Coordinate[]{startLeft, endLeft});
        LineSegment spineSkeletonLine = new LineSegment(startLeft, endLeft);

        return 0;

    }



    public boolean verifyBasePoints(ImagePlus imp, Point left_p, Point right_p) {
        Coordinate baseLeftBoundary = new Coordinate(left_p.x, left_p.y),
                   baseRightBoundary = new Coordinate(right_p.x, right_p.y);

        LineSegment baseLine = new LineSegment(baseLeftBoundary, baseRightBoundary);
        int i = 0;

        if ( baseLine.getLength() > 40 )
            return false;

        while ( verifyPoint(imp, new Point((int) baseLeftBoundary.x, (int) baseLeftBoundary.y))
                &&
                verifyPoint(imp, new Point((int) baseRightBoundary.x, (int) baseRightBoundary.y)) ) {
            i++;
            baseLeftBoundary = baseLine.pointAlong(- i/baseLine.getLength());
            baseRightBoundary = baseLine.pointAlong(1 + i/baseLine.getLength());
            if ( i > maxDistanceToBaselineEnd*2 ) {
                return false;
            }
        }
        return true;
    }



    public boolean verifyPoint(ImagePlus imp, Point point) {
        return imageProcessor.getPixel(point.x, point.y) > 0;
    }


    public boolean verifyCoordinate(ImagePlus imp, Coordinate coordinate) {
        return imageProcessor.getPixel((int) coordinate.x, (int) coordinate.y) > 0;

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
