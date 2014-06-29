package edu.ysu.itrace;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.internal.PartSite;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.UIJob;

import edu.ysu.itrace.exceptions.CalibrationException;
import edu.ysu.itrace.exceptions.EyeTrackerConnectException;
import edu.ysu.itrace.gaze.GazeHandlerFactory;
import edu.ysu.itrace.gaze.IGazeHandler;
import edu.ysu.itrace.gaze.IGazeResponse;

/**
 * ViewPart for managing and controlling the plugin.
 */
@SuppressWarnings("restriction")
public class ControlView extends ViewPart implements IPartListener2,
                                                     ShellListener {
    private static final int POLL_GAZES_MS = 5;
    private static final String EOL = System.getProperty("line.separator");
    public static final String KEY_AST = "itraceAST";

    private IEyeTracker tracker;
    private GazeRepository gazeRepository;
    private Shell rootShell;

    private GazeTransport gazeTransport;
    private LinkedBlockingQueue<Gaze> standardTrackingQueue;
    private LinkedBlockingQueue<Gaze> crosshairQueue;

    private volatile boolean trackingInProgress;
    private LinkedBlockingQueue<IGazeResponse> gazeResponses
            = new LinkedBlockingQueue<IGazeResponse>();

    private int line_height, font_height;
    private String gazeFilename = "";

    /*
     * Gets gazes from the eye tracker, calls gaze handlers, and adds responses
     * to the queue for
     * the response handler thread to process.
     */
    private UIJob gazeHandlerJob =  new UIJob("Tracking Gazes") {
        {
            setPriority(INTERACTIVE);
        }

        @Override
        public IStatus runInUIThread(IProgressMonitor monitor) {
            if (standardTrackingQueue == null)
                return Status.OK_STATUS;

            int loops = 0;
            Gaze g = null;
            do
            {
                g = standardTrackingQueue.poll();
                if (g != null) {
                    Dimension screenRect = Toolkit.getDefaultToolkit()
                                           .getScreenSize();
                    int screenX = (int) (g.getX() * screenRect.width);
                    int screenY = (int) (g.getY() * screenRect.height);
                    IGazeResponse response = handleGaze(screenX, screenY, g);

                    if(response != null){
                        try{
                            gazeResponses.add(response);
                        }
                        catch(IllegalStateException ise){
                            System.err.println("Error! Gaze response queue is "
                                               + "full!");
                        }
                    }
                }

                if (trackingInProgress || g != null) {
                    schedule(POLL_GAZES_MS);
                } else {
                    gazeHandlerJob.cancel();
                }
                ++loops;
            } while (loops < 15 && g != null);

            return Status.OK_STATUS;
        }
    };



    /*
     * Outputs all gaze responses to an XML file separate from the UI thread.
     */
    private class ResponseHandlerThread extends Thread {
        @Override
        public void run(){
            XMLStreamWriter responseWriter;
            FileWriter outFile;
            XMLOutputFactory outFactory = XMLOutputFactory.newInstance();
            String workspaceLocation = ResourcesPlugin.getWorkspace().getRoot()
                    .getLocation().toString();
            Dimension screenRect = Toolkit.getDefaultToolkit().getScreenSize();

            System.out.println("Putting files: " + workspaceLocation + "/" + gazeFilename);
            try {
                outFile = new FileWriter(workspaceLocation + "/" +
                                         gazeFilename);
                responseWriter = outFactory.createXMLStreamWriter(outFile);
                responseWriter.writeStartDocument("utf-8");
                responseWriter.writeCharacters(EOL);
                responseWriter.writeStartElement("itrace-records");
                responseWriter.writeCharacters(EOL);
                responseWriter.writeStartElement("environment");
                responseWriter.writeCharacters(EOL);
                responseWriter.writeEmptyElement("screen-size");
                responseWriter.writeAttribute("width",
                        String.valueOf(screenRect.width));
                responseWriter.writeAttribute("height",
                        String.valueOf(screenRect.height));
                responseWriter.writeCharacters(EOL);
                responseWriter.writeStartElement("line-height");
                responseWriter.writeCharacters(String.valueOf(line_height));
                responseWriter.writeEndElement();
                responseWriter.writeCharacters(EOL);
                responseWriter.writeStartElement("font-height");
                responseWriter.writeCharacters(String.valueOf(font_height));
                responseWriter.writeEndElement();
                responseWriter.writeCharacters(EOL);
                responseWriter.writeEndElement();
                responseWriter.writeCharacters(EOL);
                responseWriter.writeStartElement("gazes");
                responseWriter.writeCharacters(EOL);
            } catch (Exception e) {
                throw new RuntimeException("Log files could not be created." +
                                           e.getMessage());
            }



            while(true){
                if(!trackingInProgress && gazeResponses.size() <= 0){
                    break;
                }

                IGazeResponse response = gazeResponses.poll();

                //TODO: Move this code somewhere else.
                if(response != null){
                    try {
                        if(response.getProperties().size() > 0){
                            int screenX = (int) (screenRect.width * response
                                          .getGaze().getX());
                            int screenY = (int) (screenRect.height * response
                                          .getGaze().getY());

                            responseWriter.writeEmptyElement("response");
                            responseWriter.writeAttribute("file",
                                    response.getName());
                            responseWriter.writeAttribute("type",
                                    response.getType());
                            responseWriter.writeAttribute("x",
                                    String.valueOf(screenX));
                            responseWriter.writeAttribute("y",
                                    String.valueOf(screenY));
                            responseWriter.writeAttribute("left-validation",
                                    String.valueOf(response.getGaze()
                                    .getLeftValidity()));
                            responseWriter.writeAttribute("right-validation",
                                    String.valueOf(response.getGaze()
                                    .getRightValidity()));
                            responseWriter.writeAttribute("timestamp",
                                    String.valueOf(response.getGaze()
                                    .getTimeStamp().getTime()));

                            for(Iterator<Entry<String,String>> entries
                                = response.getProperties().entrySet()
                                .iterator();
                                    entries.hasNext(); ){
                                Entry<String,String> pair = entries.next();
                                responseWriter.writeAttribute(pair.getKey(),
                                                              pair.getValue());
                            }
                            responseWriter.writeCharacters(EOL);
                        }
                    } catch (XMLStreamException e) {
                        // ignore write errors
                    }
                }
            }

            try {
                responseWriter.writeEndElement();
                responseWriter.writeCharacters(EOL);
                responseWriter.writeEndElement();
                responseWriter.writeCharacters(EOL);
                responseWriter.writeEndDocument();
                responseWriter.writeCharacters(EOL);
                responseWriter.flush();
                responseWriter.close();
                outFile.close();
                System.out.println("Gaze responses saved.");
            } catch (XMLStreamException | IOException e) {
                // ignore write errors
            }
        }
    }

    private ResponseHandlerThread responseHandlerThread = null;




    @Override
    public void createPartControl(Composite parent) {
        // find root shell
        rootShell = parent.getShell();
        while (rootShell.getParent() != null) {
            rootShell = rootShell.getParent().getShell();
        }
        rootShell.addShellListener(this);

        // add listener for determining part visibility
        getSite().getWorkbenchWindow().getPartService().addPartListener(this);

        // set up UI
        Button calibrateButton = new Button(parent, SWT.PUSH);
        calibrateButton.setText("Calibrate");
        calibrateButton.addSelectionListener(new SelectionAdapter(){
            @Override
            public void widgetSelected(SelectionEvent e) {
                if(tracker != null){
                    try {
                        tracker.calibrate();
                    } catch (CalibrationException e1) {
                        displayError("Failed to calibrate. Reason: "
                                     + e1.getMessage());
                    }
                }
            }
        });

        final Button startButton = new Button(parent, SWT.PUSH);
        startButton.setText("Start Tracking");
        startButton.addSelectionListener(new SelectionAdapter(){
            @Override
            public void widgetSelected(SelectionEvent e) {
                startTracking();
            }
        });

        final Button stopButton = new Button(parent, SWT.PUSH);
        stopButton.setText("Stop Tracking");
        stopButton.addSelectionListener(new SelectionAdapter(){
            @Override
            public void widgetSelected(SelectionEvent e) {
                stopTracking();
            }
        });

        final Button displayStatus = new Button(parent, SWT.PUSH);
        displayStatus.setText("Display Status");
        displayStatus.addSelectionListener(new SelectionAdapter(){
            @Override
            public void widgetSelected(SelectionEvent e) {
                (new EyeStatusView(rootShell, gazeTransport)).open();
            }
        });

        final Button display_crosshair = new Button(parent, SWT.CHECK);
        display_crosshair.setText("Display Crosshair");
        display_crosshair.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    tracker.displayCrosshair(display_crosshair.getSelection());
                    //Create a client for the crosshair so that it will continue
                    //to run when tracking is disabled. Remove when done.
                    if (display_crosshair.getSelection()) {
                        if (crosshairQueue == null)
                            crosshairQueue = gazeTransport.createClient();
                    } else {
                        if (crosshairQueue != null) {
                            gazeTransport.removeClient(crosshairQueue);
                            crosshairQueue = null;
                        }
                    }
                }
            });

        final Text xDrift = new Text(parent, SWT.LEFT);
        xDrift.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                tracker.setXDrift(Integer.parseInt(xDrift.getText()));
            }
        });

        final Text yDrift = new Text(parent, SWT.LEFT);
        yDrift.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                tracker.setYDrift(Integer.parseInt(yDrift.getText()));
            }
        });

        final Text gazeFilename = new Text(parent, SWT.LEFT);
        gazeFilename.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                ControlView.this.gazeFilename = gazeFilename.getText();
            }
        });
        gazeFilename.setText("gaze-responses-" + (new Date()).getTime() +
                             ".xml");

        selectTracker(0); // TODO allow user to select the right tracker
    }

    @Override
    public void dispose(){
        if (gazeTransport != null)
            gazeTransport.quit();

        stopTracking();
        if(tracker != null){
            tracker.close();
        }
        getSite().getWorkbenchWindow().getPartService()
                .removePartListener(this);
        super.dispose();
    }

    @Override
    public void setFocus() {}

    @Override
    public void partActivated(IWorkbenchPartReference partRef) {}

    @Override
    public void partBroughtToTop(IWorkbenchPartReference partRef) {}

    @Override
    public void partClosed(IWorkbenchPartReference partRef) {}

    @Override
    public void partDeactivated(IWorkbenchPartReference partRef) {}

    @Override
    public void partOpened(IWorkbenchPartReference partRef) {}

    @Override
    public void partInputChanged(IWorkbenchPartReference partRef) {}

    @Override
    public void partVisible(IWorkbenchPartReference partRef) {
        setupStyledText(partRef);
        HandlerBindManager.bind(partRef);
    }

    @Override
    public void partHidden(IWorkbenchPartReference partRef) {
        HandlerBindManager.unbind(partRef);
    }

    @Override
    public void shellActivated(ShellEvent e) {
        gazeHandlerJob.schedule(POLL_GAZES_MS);
    }

    @Override
    public void shellClosed(ShellEvent e) {
        gazeHandlerJob.cancel();
    }

    @Override
    public void shellDeactivated(ShellEvent e) {
        gazeHandlerJob.cancel();
    }

    @Override
    public void shellDeiconified(ShellEvent e) {
        gazeHandlerJob.schedule(POLL_GAZES_MS);
    }

    @Override
    public void shellIconified(ShellEvent e) {
        gazeHandlerJob.cancel();
    }

    /**
     * Find a styled text control within a part, set it up to be used by iTrace,
     * and extract meta-data from it.
     * @param partRef Highest-level part reference possible.
     */
    private void setupStyledText(IWorkbenchPartReference partRef) {
        Shell workbenchShell = partRef.getPage().getWorkbenchWindow().
                               getShell();
        for (Control control : workbenchShell.getChildren())
            setupStyledText(control);
    }

    /**
     * Recursive helper method for setupStyledText(IWorkbenchPartReference).
     * @param control Control under which to recursively search for styled text.
     */
    private void setupStyledText(Control control) {
        if (control instanceof StyledText) {
            StyledText styledText = (StyledText) control;
            this.line_height = styledText.getLineHeight();
            this.font_height = styledText.getFont()
                               .getFontData()[0].getHeight();
            if (styledText.getData(KEY_AST) == null)
                styledText.setData(KEY_AST, new AstManager(styledText));
        }

        if (control instanceof Composite) {
            Composite composite = (Composite) control;
            for (Control curControl : composite.getChildren()) {
                if (control != null)
                    setupStyledText(curControl);
            }
        }
    }

    /*
     * Finds the control under the specified screen coordinates and calls
     * its gaze handler on the localized point. Returns the gaze response
     * or null if the gaze is not handled.
     */
    private IGazeResponse handleGaze(int screenX, int screenY, Gaze gaze){

        Queue<Control[]> childrenQueue = new LinkedList<Control[]>();
        childrenQueue.add(rootShell.getChildren());

        Rectangle monitorBounds = rootShell.getMonitor().getBounds();

        while(!childrenQueue.isEmpty()){
            for(Control child : childrenQueue.remove()){
                Rectangle childScreenBounds = child.getBounds();
                Point screenPos = child.toDisplay(0,0);
                childScreenBounds.x = screenPos.x - monitorBounds.x;
                childScreenBounds.y = screenPos.y - monitorBounds.y;
                if(childScreenBounds.contains(screenX, screenY)){
                    if(child instanceof Composite){
                        Control[] nextChildren
                                = ((Composite)child).getChildren();
                        if(nextChildren.length > 0 && nextChildren[0] != null){
                            childrenQueue.add(nextChildren);
                        }
                    }

                    IGazeHandler handler = (IGazeHandler) child.getData(
                            HandlerBindManager.KEY_HANDLER);
                    if(handler != null){
                        return handler.handleGaze(screenX - childScreenBounds.x,
                                screenY - childScreenBounds.y, gaze);
                    }
                }
            }
        }

        return null;
    }


    private void listTrackers(){
        ArrayList<IEyeTracker> trackers
            = EyeTrackerFactory.getAvailableEyeTrackers();
    }

    private void selectTracker(int index) {
        try {
            tracker = EyeTrackerFactory.getConcreteEyeTracker(index);
            gazeTransport = new GazeTransport(tracker);
            gazeTransport.start();
        } catch (EyeTrackerConnectException | IOException e) {
            throw new RuntimeException("Could not connect to eye tracker.");
        }
    }

    private void startTracking(){
        if(trackingInProgress){
            return;
        }

        //Check that file does not already exist. If it does, do not begin
        //tracking.
        String workspaceLocation = ResourcesPlugin.getWorkspace().getRoot().
                                   getLocation().toString();
        File fileAtPath = new File(workspaceLocation + "/" + gazeFilename);
        if (fileAtPath.exists()) {
            MessageBox messageBox = new MessageBox(rootShell);
            messageBox.setMessage("You cannot overwrite this file. If you " +
                                  "wish to continue, delete the file " +
                                  "manually.");
            messageBox.open();
            return;
        }

        if (tracker != null) {
            standardTrackingQueue = gazeTransport.createClient();

            if (standardTrackingQueue != null &&
                    responseHandlerThread == null) {
                responseHandlerThread = new ResponseHandlerThread();
                responseHandlerThread.start();
                gazeHandlerJob.schedule(POLL_GAZES_MS);
                trackingInProgress = true;
            }
        }
    }

    private void stopTracking(){
        if (!trackingInProgress){
            return;
        }

        if (tracker != null) {
            if (gazeTransport.removeClient(standardTrackingQueue)) {
                trackingInProgress = false;
                standardTrackingQueue = null;
                responseHandlerThread = null;
            }
        }
    }

    private void displayError(String message)
    {
        MessageBox error_box = new MessageBox(rootShell, SWT.ICON_ERROR);
        error_box.setMessage(message);
        error_box.open();
    }
}
