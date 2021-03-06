/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.hadoop.hive.llap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.conf.HiveConf.ConfVars;
import org.apache.hadoop.security.SecurityUtil;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LlapUtil {
  private static final Logger LOG = LoggerFactory.getLogger(LlapUtil.class);

  public static String getDaemonLocalDirList(Configuration conf) {
    String localDirList = HiveConf.getVar(conf, ConfVars.LLAP_DAEMON_WORK_DIRS);
    if (localDirList != null && !localDirList.isEmpty()) return localDirList;
    return conf.get("yarn.nodemanager.local-dirs");
  }

  public static UserGroupInformation loginWithKerberos(
      String principal, String keytabFile) throws IOException {
    if (!UserGroupInformation.isSecurityEnabled()) return null;
    if (principal.isEmpty() || keytabFile.isEmpty()) {
      throw new RuntimeException("Kerberos principal and/or keytab are empty");
    }
    LOG.info("Logging in as " + principal + " via " + keytabFile);
    return UserGroupInformation.loginUserFromKeytabAndReturnUGI(
        SecurityUtil.getServerPrincipal(principal, "0.0.0.0"), keytabFile);
  }

  private final static Pattern hostsRe = Pattern.compile("[^A-Za-z0-9_-]");
  public static String generateClusterName(Configuration conf) {
    String hosts = HiveConf.getTrimmedVar(conf, ConfVars.LLAP_DAEMON_SERVICE_HOSTS);
    return hostsRe.matcher(hosts.startsWith("@") ? hosts.substring(1) : hosts).replaceAll("_");
  }

  public static String getUserNameFromPrincipal(String principal) {
    // Based on SecurityUtil.
    if (principal == null) return null;
    String[] components = principal.split("[/@]");
    return (components == null || components.length != 3) ? principal : components[0];
  }

  public static List<StatisticsData> getStatisticsForScheme(final String scheme,
      final List<StatisticsData> stats) {
    List<StatisticsData> result = new ArrayList<>();
    if (stats != null && scheme != null) {
      for (StatisticsData s : stats) {
        if (s.getScheme().equalsIgnoreCase(scheme)) {
          result.add(s);
        }
      }
    }
    return result;
  }

  public static Map<String, FileSystem.Statistics> getCombinedFileSystemStatistics() {
    final List<FileSystem.Statistics> allStats = FileSystem.getAllStatistics();
    final Map<String, FileSystem.Statistics> result = new HashMap<>();
    for (FileSystem.Statistics statistics : allStats) {
      final String scheme = statistics.getScheme();
      if (result.containsKey(scheme)) {
        FileSystem.Statistics existing = result.get(scheme);
        FileSystem.Statistics combined = combineFileSystemStatistics(existing, statistics);
        result.put(scheme, combined);
      } else {
        result.put(scheme, statistics);
      }
    }
    return result;
  }

  private static FileSystem.Statistics combineFileSystemStatistics(final FileSystem.Statistics s1,
      final FileSystem.Statistics s2) {
    FileSystem.Statistics result = new FileSystem.Statistics(s1);
    result.incrementReadOps(s2.getReadOps());
    result.incrementLargeReadOps(s2.getLargeReadOps());
    result.incrementWriteOps(s2.getWriteOps());
    result.incrementBytesRead(s2.getBytesRead());
    result.incrementBytesWritten(s2.getBytesWritten());
    return result;
  }

  public static List<StatisticsData> cloneThreadLocalFileSystemStatistics() {
    List<StatisticsData> result = new ArrayList<>();
    // thread local filesystem stats is private and cannot be cloned. So make a copy to new class
    for (FileSystem.Statistics statistics : FileSystem.getAllStatistics()) {
      result.add(new StatisticsData(statistics.getScheme(), statistics.getThreadStatistics()));
    }
    return result;
  }

  public static class StatisticsData {
    long bytesRead;
    long bytesWritten;
    int readOps;
    int largeReadOps;
    int writeOps;
    String scheme;

    public StatisticsData(String scheme, FileSystem.Statistics.StatisticsData fsStats) {
      this.scheme = scheme;
      this.bytesRead = fsStats.getBytesRead();
      this.bytesWritten = fsStats.getBytesWritten();
      this.readOps = fsStats.getReadOps();
      this.largeReadOps = fsStats.getLargeReadOps();
      this.writeOps = fsStats.getWriteOps();
    }

    public long getBytesRead() {
      return bytesRead;
    }

    public long getBytesWritten() {
      return bytesWritten;
    }

    public int getReadOps() {
      return readOps;
    }

    public int getLargeReadOps() {
      return largeReadOps;
    }

    public int getWriteOps() {
      return writeOps;
    }

    public String getScheme() {
      return scheme;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(" scheme: ").append(scheme);
      sb.append(" bytesRead: ").append(bytesRead);
      sb.append(" bytesWritten: ").append(bytesWritten);
      sb.append(" readOps: ").append(readOps);
      sb.append(" largeReadOps: ").append(largeReadOps);
      sb.append(" writeOps: ").append(writeOps);
      return sb.toString();
    }
  }
}
