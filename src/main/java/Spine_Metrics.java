import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.*;
import ij.measure.Measurements;
import ij.plugin.Commands;
import ij.plugin.Selection;
import ij.plugin.filter.Convolver;
import ij.plugin.frame.PlugInFrame;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.util.Tools;
import org.locationtech.jts.algorithm.Distance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.*;

public class Spine_Metrics extends PlugInFrame implements MouseListener, KeyListener {

    Panel panel;
    Choice modeChoice;
    TextField scaleTextField;

    private static final String NORMAL = "Normal";
    private static final String FLOATING = "Floating spine";
    private static final int NORMAL_INT = 0;
    private static final int FLOATING_INT = 1;

    private ImageCanvas canvas;
    private static Frame instance;
    private ImagePlus img;
    private ImageWindow win;
    private ImageProcessor imageProcessor;
    PointRoi pointRoi;
    private int baseLineOrientation; /**Horizontal = 0, Vertival = 1 */
    private static final Point nullPoint = new Point(-1, -1);
    private Point p_l, p_r, p_c;
    private int prevImgHash;
    private int mode;

    public Spine_Metrics() {
        super("Testing");
        instance = this;
        IJ.setTool(7);

        try {
            panel = new Panel();
            panel.setLayout(new GridLayout(0,2));
            panel.setBackground(SystemColor.control);

            Label title = new Label();
            title.setText("Calculating metrics");
            panel.add(title);
            panel.add(new Label());

            Label modeLabel = new Label("Mode:");
            panel.add(modeLabel);
            modeChoice = new Choice();
            modeChoice.add(NORMAL);
            modeChoice.add(FLOATING);
            panel.add(modeChoice);

            Label scaleLabel = new Label("Scale:");
            panel.add(scaleLabel);
            scaleTextField = new TextField(Double.toString(1.0));
            panel.add(scaleTextField);

            add(panel,BorderLayout.CENTER);
            pack();
            setVisible(true);

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
            imageProcessor.snapshot();
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
            mode = NORMAL_INT;
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
        if (isSingle4Neighboured(x,y)) {
            IJ.showMessage("Wrong Input", "Can't choose point with a single neighbour");
            return;
        }
        img = canvas.getImage();
        imageProcessor.snapshot();
        p = new Point(x,y);

        pointRoi = new PointRoi(x,y,img);
        imageProcessor.setRoi(pointRoi);
        //pointRoi.draw(img.getImage().getGraphics());

        if (modeChoice.getSelectedItem().equals(NORMAL)){
            mode = NORMAL_INT;
            if (verifyPoint(img, p)) {
                //imageProcessor.drawDot(p.x, p.y);
                if (p_l.equals(nullPoint))
                    p_l = new Point(p);
                else {
                    baseLineOrientation = 1;
                    if (isBaseLineHorizontal(p_l.x, p_l.y, p.x, p.y)) {
                        baseLineOrientation = 0;
                        if (p.x < p_l.x) {
                            p_r = new Point(p_l);
                            p_l = new Point(p);
                        } else
                            p_r = new Point(p);
                    } else if (p.y < p_l.y) {
                        p_r = new Point(p_l);
                        p_l = new Point(p);
                    } else
                        p_r = new Point(p);
                    process2(img);
                }
            } else {
                IJ.showMessage("Wrong Input", "Can't choose background pixels");
            }
        } else {
            mode = FLOATING_INT;
            if (verifyPoint(img, p)) {
                //imageProcessor.drawDot(p.x, p.y);
                if (p_l.equals(nullPoint))
                    p_l = new Point(p);
                else {
                    p_c = new Point(p);
                    process2(img);
                }
            }
        }
    }

    boolean isBaseLineHorizontal(int x0, int y0, int x1, int y1) {
        if (y0 == y1) return true;
        return (Math.abs(x1-x0)/Math.abs(y1-y0) >= 1);
    }

    void process2(ImagePlus img) {
        //imageProcessor.snapshot();
        Point nextPoint;
        Point previous;
        LinkedHashSet<Point> edge = new LinkedHashSet<>();
        LinkedHashSet<Point> resEdge;
        edge.add(new Point(p_l));
        boolean isMovingUp = false;
        boolean isMovingLeft = false;

        if (mode == FLOATING_INT) {
            if (verifyPoint(img, p_l.x-1, p_l.y)) {
                p_r = new Point(p_l.x-1, p_l.y);
                baseLineOrientation = 0;
            } else if (verifyPoint(img, p_l.x, p_l.y-1)) {
                p_r = new Point(p_l.x, p_l.y-1);
                baseLineOrientation = 1;
            } else if ((!verifyPoint(img, p_l.x-1, p_l.y)) && (!verifyPoint(img, p_l.x, p_l.y-1))) {
                if (verifyPoint(img, p_l.x+1, p_l.y)) {
                    p_r = new Point(p_l);
                    p_l.x+=1;
                    baseLineOrientation = 0;
                } else {
                    IJ.showMessage("Error", "Unable to recognize structure");
                    p_l = p_r = p_c = nullPoint;
                    return;
                }
            }
        }

        nextPoint = new Point(p_l);

        if (baseLineOrientation == 0) {

            while (verifyPoint(img, nextPoint.x+1, nextPoint.y)) {
                nextPoint.x+=1;
                if (!edge.add(new Point(nextPoint))) {
                    IJ.showMessage("Error", "Unable to parse the edge");
                    p_l = p_r = p_c = nullPoint;
                    return;
                }
            }

            if (verifyPoint(img, nextPoint.x, nextPoint.y-1)) {
                previous = new Point(nextPoint);
                nextPoint.y-=1;
                isMovingUp = true;
                if (!edge.add(new Point(nextPoint))) {
                    IJ.showMessage("Error", "Unable to parse the edge");
                    p_l = p_r = p_c = nullPoint;
                    return;
                }
            } else if (verifyPoint(img, nextPoint.x, nextPoint.y+1)) {
                previous = new Point(nextPoint);
                nextPoint.y+=1;
                if (!edge.add(new Point(nextPoint))) {
                    IJ.showMessage("Error", "Unable to parse the edge");
                    p_l = p_r = p_c = nullPoint;
                    return;
                }
            } else {
                IJ.showMessage("Error", "Unable to recognize structure");
                p_l = p_r = p_c = nullPoint;
                return;
            }
            resEdge = parseEdge(img, previous, nextPoint, edge, isMovingUp, isMovingLeft);
        } else {
            while (verifyPoint(img, nextPoint.x, nextPoint.y+1)) {
                nextPoint.y+=1;
                if (!edge.add(new Point(nextPoint))) {
                    IJ.showMessage("Error", "Unable to parse the edge");
                    p_l = p_r = p_c = nullPoint;
                    return;
                }
            }
            if (verifyPoint(img, nextPoint.x-1, nextPoint.y)) {
                previous = new Point(nextPoint);
                nextPoint.x-=1;
                isMovingLeft = true;
                if (!edge.add(new Point(nextPoint))) {
                    IJ.showMessage("Error", "Unable to parse the edge");
                    p_l = p_r = p_c = nullPoint;
                    return;
                }
            } else if (verifyPoint(img, nextPoint.x+1, nextPoint.y)) {
                previous = new Point(nextPoint);
                nextPoint.x+=1;
                if (!edge.add(new Point(nextPoint))) {
                    IJ.showMessage("Error", "Unable to parse the edge");
                    p_l = p_r = p_c = nullPoint;
                    return;
                }
            } else {
                IJ.showMessage("Error", "Unable to recognize structure");
                p_l = p_r = p_c = nullPoint;
                return;
            }
            resEdge = parseEdge(img, previous, nextPoint, edge, isMovingUp, isMovingLeft);
        }

        if (resEdge.isEmpty()) {
            p_l = p_r = p_c = nullPoint;
            return;
        }

        int[] xp = new int[resEdge.size()/2 + 1];
        int[] yp = new int[resEdge.size()/2 + 1];
        int nPoints = 0;
        ArrayList<Point> edgeArray =new ArrayList<>();
        edgeArray.addAll(resEdge);
        LineSegment baseLine = new LineSegment(p_l.x, p_l.y, p_r.x, p_r.y);
        LineSegment maxLine = new LineSegment(edgeArray.get(0).x, edgeArray.get(0).y, edgeArray.get(edge.size()-1).x, edgeArray.get(edge.size()-1).y);
        boolean isNewMax = false;
        for (int i = 0; i < resEdge.size()/2; i++) {
            LineSegment line = new LineSegment(edgeArray.get(i).x, edgeArray.get(i).y, edgeArray.get(resEdge.size()-i-1).x, edgeArray.get(resEdge.size()-i-1).y);
            if (line.getLength() > maxLine.getLength()+2) {
                maxLine = line;
                isNewMax = true;
            }
            xp[i] = (int)line.midPoint().x;
            yp[i] = (int)line.midPoint().y;
            nPoints++;
        }
        IJ.setTool(6);
        PolygonRoi skeleton = new PolygonRoi(xp, yp, nPoints, Roi.FREELINE);
        PolygonRoi maxPolyLine = new PolygonRoi(new int[]{(int)maxLine.p0.x, (int)maxLine.p1.x},
                                                new int[]{(int)maxLine.p0.y, (int)maxLine.p1.y}, 2, Roi.FREELINE);

        //imageProcessor.setRoi(skeleton);
        //imageProcessor.setRoi(maxPolyLine);

        //skeleton.setStrokeColor(Color.WHITE);
        //maxPolyLine.setStrokeColor(Color.WHITE);
        //imageProcessor.drawLine((int)maxLine.p0.x, (int)maxLine.p0.y, (int)maxLine.p1.x, (int)maxLine.p1.y);
        //imageProcessor.drawRoi(skeleton);
        //imageProcessor.drawRoi(maxPolyLine);

        //skeleton.draw(img.getImage().getGraphics());
        //maxPolyLine.draw(img.getImage().getGraphics());

        LineSegment neck;
        if (mode == FLOATING_INT) {
            //neck = new LineSegment(p_c.x, p_c.y, p_l.x, p_l.y);
            //PolygonRoi neckLine = new PolygonRoi(new int[]{(int) neck.p0.x, (int) neck.p1.x},
                    //new int[]{(int) neck.p0.y, (int) neck.p1.y}, 2, Roi.POLYLINE);
            imageProcessor.drawLine(p_c.x, p_c.y, p_l.x, p_l.y);
        }

        String string = "";
        for (Point p : resEdge) {
            imageProcessor.drawDot(p.x, p.y);
        }



        //IJ.setTool(3);
        PolygonRoi contourRoi = new PolygonRoi( edgeArray.stream().mapToInt(px -> px.x).toArray(),
                                                edgeArray.stream().mapToInt(px -> px.y).toArray(),
                                                edgeArray.size(),
                                                Roi.POLYGON);
        RoiManager roiManager = new RoiManager();
        roiManager.addRoi(contourRoi);
        roiManager.multiMeasure(img).show("Results");

        double scaleValue = Tools.parseDouble(scaleTextField.getText());

        String type = "";
        if (isNewMax)
            type = "Headed";
        else
            type = "Stubby";

        if (mode == FLOATING_INT) {
            neck = new LineSegment(p_c.x, p_c.y, p_l.x, p_l.y);
            IJ.showMessage("Floating spine metrics",
                    "Type: " + "Headed" + "\n" +
                            "Perimeter= " + (edge.size()+neck.getLength()*2)*scaleValue + "\n" +
                            "Head Length= " + skeleton.size()*scaleValue + "\n" +
                            "Head Width= " + (maxLine.getLength()*scaleValue) + "\n" +
                            "Head Perimeter= " + edge.size()*scaleValue + "\n" +
                            "Neck Length= " + neck.getLength()*scaleValue + "\n" +
                            "Summary Length= " + (skeleton.size()+neck.getLength())*scaleValue);
        } else
            IJ.showMessage("Metrics",
                "Type: " + type + "\n" +
                    "Perimeter= " + ((edge.size()+baseLine.getLength())*scaleValue) + "\n" +
                    "Head Width= " + maxLine.getLength()*scaleValue + "\n" +
                    "Head Perimeter= " + edge.size()*scaleValue + "\n" +
                    "Skeleton Length= " + skeleton.size()*scaleValue);

        p_l = p_r = p_c = nullPoint;
        //IJ.setTool(7);
    }

    private LinkedHashSet<Point> parseEdge(ImagePlus img, Point previous, Point nextPoint, LinkedHashSet<Point> edge, boolean isMovingUp, boolean isMovingLeft) {
        while (!nextPoint.equals(p_r)) {

            /**LEFT*/
            if (verifyPoint(img, nextPoint.x-1, nextPoint.y)
                    && !isPrevious(previous, nextPoint.x-1, nextPoint.y)
                    && !isSingle4Neighboured(nextPoint.x-1, nextPoint.y)) {
                if (verifyPoint(img, nextPoint.x+1, nextPoint.y) && !isMovingLeft) {
                    previous = new Point(nextPoint);
                    nextPoint.x += 1;
                } else {
                    previous = new Point(nextPoint);
                    nextPoint.x -= 1;
                    isMovingLeft = true;
                }
                if (!edge.add(new Point(nextPoint))) {
                    IJ.showMessage("Error", "Unable to parse the edge");
                    return new LinkedHashSet<>();
                }
            }

            /**UP*/
            else if (verifyPoint(img, nextPoint.x, nextPoint.y-1)
                    && !isPrevious(previous, nextPoint.x, nextPoint.y-1)
                    && !isSingle4Neighboured(nextPoint.x, nextPoint.y-1)) {
                if (verifyPoint(img, nextPoint.x, nextPoint.y+1) && !isMovingUp ) {
                    previous = new Point(nextPoint);
                    nextPoint.y += 1;
                }
                else {
                    previous = new Point(nextPoint);
                    nextPoint.y -= 1;
                    isMovingUp = true;
                }
                if (!edge.add(new Point(nextPoint))) {
                    IJ.showMessage("Error", "Unable to parse the edge");
                    return new LinkedHashSet<>();
                }
            }

            /**RIGHT*/
            else if (verifyPoint(img, nextPoint.x+1, nextPoint.y)
                    && !isPrevious(previous, nextPoint.x+1, nextPoint.y)
                    && !isSingle4Neighboured(nextPoint.x+1, nextPoint.y)) {
                previous = new Point(nextPoint);
                nextPoint.x += 1;
                isMovingLeft = false;
                if (!edge.add(new Point(nextPoint))) {
                    IJ.showMessage("Error", "Unable to parse the edge");
                    return new LinkedHashSet<>();
                }
            }

            /**DOWN*/
            else if (verifyPoint(img, nextPoint.x, nextPoint.y+1)
                    && !isPrevious(previous, nextPoint.x, nextPoint.y+1)
                    && !isSingle4Neighboured(nextPoint.x, nextPoint.y+1)) {
                previous = new Point(nextPoint);
                isMovingUp=false;
                nextPoint.y += 1;
                if (!edge.add(new Point(nextPoint))) {
                    IJ.showMessage("Error", "Unable to parse the edge");
                    return new LinkedHashSet<>();
                }
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

    @Override
    public void keyTyped(KeyEvent e) {
        IJ.controlKeyDown();
    }

    @Override
    public void keyPressed(KeyEvent e) {

    }

    @Override
    public void keyReleased(KeyEvent e) {

    }
}
