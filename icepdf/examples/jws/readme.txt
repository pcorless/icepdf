** ICEpdf Java Web Start Demo *

The Java Web Start (JWS) example main purpose is to show how to build a deploy a JWS project.  The example uses
a Netbeans build script to produce the necessary artifacts for a JWS deployment.  JWS is inherently more secure then
Applets but this comes at the cost of complicating the build process.

** Signing the Jar files *

All the jars files in a JWS project must be signed using the same code signer certificate in order to be deployed
successfully.  The build.xml contains a signjar target for signing all the jars in the projects.  The signjar
target should updated to reflect the properties of a specified keystore.   More information on the jar signing
process can be found at https://docs.oracle.com/javase/tutorial/deployment/jar/signing.html.

** Manifest and Jar Contents*

One of the new security features of JWS projects is that the main jar must contain a copy of the external JNLP file so
that it can be checked against the signed jar copy for potential modification.  The Manifest also plays an important
part in the security model allowing for the specification of the main class, code base and permissions.

** Codebase vs No Codebase*

Two launch scenarios are provided with this demo, the first *_no_codebase.jnlp contains an JNLP file that that does not
specify the codebase or href attributes on the JNLP tag.  The *_no_codebase.jnlp configuration is designed to aid
development on a local machine not running a web server.   The codebase JNLP file is what should be configured for a
production environment and it's very important that the codebase value in the manifest.mf matches that of the JNLP file.
For local development the _no_codebase.jnlp file can have a codebase of * but this should be avoided for any none
development project.

** Notes: *

* Java Cache Viewer
- Bring up the Java Cache Viewer with the command: >javaws -viewer
- this is handy fo removing cached JWS application.
- also brings up the Java Control panel.

* Advanced debugging
- one frustrating part of JWS development is the lack of feedback when a project fails to launch.
- The following link is an excellent resource for how to turn on trace level logging:
   https://docs.oracle.com/javase/7/docs/webnotes/tsg/TSG-Desktop/html/plugin.html#gcexdm

* Running the JWS demo locally
- javaws -verbose ./dist/icepdf_viewer_webstart_no_codebase.jnlp

