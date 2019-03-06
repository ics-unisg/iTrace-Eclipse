# iTrace - Plugin for an Eye-Enabled IDE

iTrace is a plugin for the Eclipse IDE.  It interfaces with an eye tracker to determine the type of element one is looking at. In the case of source code, it will determine the location of eye gaze and map it to the source code element (method call, if statement, etc...). The data generated by iTrace has been used in program comprehension and software traceability.  It has 
applications in code reading, code summarization as well as providing recommendations to developers based on eye movements.

## Requirements
* Project must be checked out as "iTrace" in your workspace.
* Eclipse IDE (Windows users should install Eclipse IDE for Java EE Developers)
* Java development kit (JDK)
* Apache IvyDE Eclipse Plugin (https://ant.apache.org/ivy/ivyde/)

## How to Build and Run
1. Install all requirements and resolve Ivy dependencies (secondary click
   project, then click Ivy -> Resolve, then refresh the project).
2. Build and install plugin binaries or click "Run" from the Eclipse workspace
   and choose "Eclipse Application".
3. Open the "iTrace" perspective. If it is not visible, click the
   "Open Perspective" icon next to the "Java" perspective icon (by default in
   the top right corner) and choose "iTrace" from the list.
4. Open the iTrace controller view on the bottom panel.

## Usage

To get eye tracking data, this plugin uses the iTrace-Core. You must have it before you can use the extension. It is available here: https://github.com/iTrace-Dev/iTrace-Core

1. Load up a project to run eye tracking on. 
2. You should see an iTrace window pane with controls. If you do not, click the "Open Perspective" icon enxt to the "Java" perspective icon (by default in the top right corner) and choose "iTrace" from the list.
3. When you are ready, click on Connect to Core. **After** that, open up the iTrace Core app, configure it, and then click Start Tracker. This way, the plugin can recieve from the core where to output the data.  
4. Now eye tracking data is being recorded. To stop it, press Disconnect. To enable a live reticle that shows where you are looking, enable Display Reticle. 

## Developer Guidelines
* Master is reserved for stable code.
* Develop all new features as a new branch.
  * Name your branch with the issue number (issue###) followed by a dash, followed by a descriptive name of the issue you are implementing in the branch.  For example issue2-calibrationGUI tells us that the branch is implementing something requested in issue 2 and is related to calibration GUI functionality.
  * Keep this branch up to date with master.
* When a branch is completed, do not merge it into master. Create a pull request
  and possibly assign a reviewer. The code reviewer will merge your code into
  master.
* Minimise inclusion of automatically generated files (i.e. if a file in the
  project can be automatically generated from another file in the project, there
  is no reason to include the generated file).
  * Executable code is an example of this. Do not include build directories.
* If possible, use Ivy to manage dependencies instead of including third-party
  libraries in this repository.
* Use CMake to build all JNI libraries.
* Never check in files of which you do not have the legal rights to publish.


## Style Guide for Developers
Try to use [Java code conventions](http://www.oracle.com/technetwork/java/javase/documentation/codeconvtoc-136057.html).
Below is an example from Eclipse.

    /**
     * A sample source file for the code formatter preview
     */

    package mypackage;

    import java.util.LinkedList;

    public class MyIntStack {
        private final LinkedList fStack;

        public MyIntStack() {
            fStack = new LinkedList();
        }

        public int pop() {
            return ((Integer) fStack.removeFirst()).intValue();
        }

        public void push(int elem) {
            fStack.addFirst(new Integer(elem));
        }

        public boolean isEmpty() {
            return fStack.isEmpty();
        }
    }
