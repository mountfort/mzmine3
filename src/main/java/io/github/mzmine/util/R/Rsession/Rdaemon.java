/*
 * Copyright (c) 2004-2022 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

/*
 * Original author: Yann Richet - https://github.com/yannrichet/rsession
 */

package io.github.mzmine.util.R.Rsession;

import io.github.mzmine.util.R.RLocationDetection;
import io.github.mzmine.util.R.Rsession.Logger.Level;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.io.FileUtils;
import org.rosuda.REngine.Rserve.RConnection;

public class Rdaemon {

  RserverConf conf;
  Process process;
  Logger log;
  static File APP_DIR = new File(FileUtils.getUserDirectory() + File.separator + ".Rserve");

  private static boolean RSERVE_INSTALLED = false;

  static {
    boolean app_dir_ok = false;
    if (!APP_DIR.exists()) {
      app_dir_ok = APP_DIR.mkdir();
    } else {
      app_dir_ok = APP_DIR.isDirectory() && APP_DIR.canWrite();
    }
    if (!app_dir_ok) {
      System.err.println("Cannot write in " + APP_DIR.getAbsolutePath());
    }
  }

  public Rdaemon(RserverConf conf, Logger log) {
    this.conf = conf;
    this.log = log;

    Runtime.getRuntime().addShutdownHook(new Thread() {

      @Override
      public void run() {
        _stop();
      }
    });
  }

  private void _stop() {
    stop();
  }

  public final static String R_HOME_KEY = "R_HOME";

  static void setRecursiveExecutable(File path) {
    for (File f : path.listFiles()) {
      if (f.isDirectory()) {
        f.setExecutable(true);
        setRecursiveExecutable(f);
      } else if (!f.canExecute() && (f.getName().endsWith(".so") || f.getName().endsWith(".dll"))) {
        f.setExecutable(true);
      }
    }

  }

  public void println(String m, Level l) {
    // System.out.println(m);
    if (log != null) {
      log.println(m, l);
    } else {
      System.out.println(l + " " + m);
    }
  }

  public void stop() {
    println("stopping R daemon... " + conf, Level.INFO);
    if (!conf.isLocal()) {
      throw new UnsupportedOperationException(
          "Not authorized to stop a remote R daemon: " + conf.toString());
    }

    try {
      RConnection s = conf.connect();
      if (s == null || !s.isConnected()) {
        println("R daemon already stoped.", Level.INFO);
        return;
      }
      s.shutdown();

    } catch (Exception ex) {
      // ex.printStackTrace();
      println(ex.getMessage(), Level.ERROR);
    }

    /*
     * if (br != null) { try { br.close(); } catch (IOException ex) { ex.printStackTrace();
     * println(ex.getMessage()); } }
     */

    // if (pw != null) {
    // pw.close();
    // }

    // process.destroy();

    println("R daemon stoped.", Level.INFO);

    log = null;
  }

  public void start(String http_proxy, String tmpDirectory) {

    if (!conf.isLocal()) {
      throw new UnsupportedOperationException(
          "Unable to start a remote R daemon: " + conf.toString());
    }

    /*
     * if (Rserve_HOME == null || !(new File(Rserve_HOME).exists())) { throw new
     * IllegalArgumentException(
     * "Rserve_HOME environment variable not correctly set.\nYou can set it using 'java ... -D" +
     * Rserve_HOME_KEY + "=[Path to Rserve] ...' startup command."); }
     */

    // Do things with Rserve install only if it hasn't been checked
    // successfully already.
    final String rCmd = RLocationDetection.getRExecutablePath();
    if (rCmd == null) {
      String notice = "Please install R";
      println(notice, Level.ERROR);
      System.err.println(notice);
      return;
    }

    if (!Rdaemon.RSERVE_INSTALLED) {
      println("checking Rserve is available... ", Level.INFO);
      Rdaemon.RSERVE_INSTALLED = StartRserve.isRserveInstalled(rCmd);

      boolean RserveInstalled = Rdaemon.RSERVE_INSTALLED;
      if (!RserveInstalled) {

        println("  no", Level.INFO);
        RserveInstalled = StartRserve.installRserve(rCmd, http_proxy, null);
        if (RserveInstalled) {
          println("  ok", Level.INFO);
        } else {
          println("  failed.", Level.ERROR);
          String notice = "Please install Rserve manually in your R environment using \"install.packages('Rserve')\" command.";
          println(notice, Level.ERROR);
          System.err.println(notice);
          return;
        }
      } else {
        println("  ok", Level.INFO);
      }
    }

    println("starting R daemon... " + conf, Level.INFO);

    StringBuffer RserveArgs = new StringBuffer("--no-save --slave ");

    // GLG TODO: Temp files usage should be enhanced to allow multiple
    // instances of MZmine
    // (currently, the first to be terminated will interfere with the
    // others... Kill their
    // Rserve instances).
    // Very much a problem while using MZmine in 'Headless' mode.
    File tmpFile, tmpDir = null;
    try {
      if (System.getProperty("os.name").contains("Win")) {
        // RserveArgs.append(" --RS-pidfile \"" + System.getenv("TEMP")
        // + "/rs_pid_" + conf.port + ".pid\"");
        tmpDir = new File((tmpDirectory != null) ? tmpDirectory : System.getenv("TEMP"));
        tmpFile = File.createTempFile("rs_pid_" + conf.port, ".pid", tmpDir);
        RserveArgs.append(" --RS-pidfile \"" + tmpFile.getPath().replaceAll("\\\\", "/") + "\"");
      } else {
        // RserveArgs.append(" --RS-pidfile \\'" +
        // System.getProperty("user.dir") + "/rs_pid.txt\\'");
        tmpDir = new File((tmpDirectory != null) ? tmpDirectory : "/tmp");
        tmpFile = File.createTempFile("rs_pid", ".pid", tmpDir);
        RserveArgs.append(" --RS-pidfile \\'" /*
         * + System.getProperty( "user.dir")
         */ + tmpFile.getPath() + "\\'");
      }
      tmpFile.deleteOnExit();
    } catch (IOException e) {
      throw new UnsupportedOperationException(
          "Unable to create temp 'rs_pid' file in directory '" + ((tmpDir != null)
              ? tmpDir.getPath() : null) + "'");
    }

    if (conf.port > 0) {
      RserveArgs.append(" --RS-port " + conf.port);
    }

    boolean started = StartRserve.launchRserve(rCmd, "--no-save --slave", RserveArgs.toString(),
        false);

    if (started) {
      println("  ok", Level.INFO);
    } else {
      println("  failed", Level.ERROR);
    }
  }

  public static String timeDigest() {
    long time = System.currentTimeMillis();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
    StringBuffer sb = new StringBuffer();
    sb = sdf.format(new Date(time), sb, new java.text.FieldPosition(0));
    return sb.toString();
  }

}
