/**
 * @author xinwuhen
 */
package com.chinaepay.wx.common;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

/**
 * @author xinwuhen
 *
 */
public class MysqlConnectionPool {
	private static MysqlConnectionPool mysqlConnPoolObj = null;
	/** 数据库连接池 **/
	public static final Map<Connection, Boolean> mysqlConntPool = new HashMap<Connection, Boolean>();
	private static MysqlDataSource mysqlDs = null;
	public static String MYSQL_URL = null;
	public static String MYSQL_USER_NAME = null;
	public static String MYSQL_USER_PASSWD = null;
	public static int MYSQL_CONN_INITAL_SIZE = 0;	// 连接池的初始连接数
	public static int MYSQL_CONN_MAX_SIZE = 0;	// 连接池最大可允许的连接数
	public static float MYSQL_CONN_INCREMENTAL_RATE = 0.00f;	// 此字段表示连接数在小于最大允许的连接数条件下，又没有可用连接供使用时，允许连接池自动增加连接对象的百分比。此处设置为0.20f,即:20%的增幅。
	public static long MYSQL_GET_CONN_SLEEP_TIME = 0;	// 此字段表示连接池内没有空闲连接且连接池的连接对象(Connection)已经达到允许的上限值(MYSQL_CONN_MAX_SIZE)时，重新从池内获取连接所等待的时间(ms)。
	public static long MYSQL_VALIDATE_CONN_INTERVAL_TIME = 0;	// 此字段表示用于校验连接是否可用的时间间隔，单位为：毫秒
	
	// 资源同步锁
	private final static Class<MysqlConnectionPool> SYNC_LOCK_OBJ = MysqlConnectionPool.class;
	
	/**
	 * 获取本类的实例。
	 * @return
	 */
	public static MysqlConnectionPool getInstance() {
		synchronized (SYNC_LOCK_OBJ) {
			if (mysqlConnPoolObj == null) {
				mysqlConnPoolObj = new MysqlConnectionPool();
			}
			return mysqlConnPoolObj;
		}
	}
	
	/**
	 * 私有化本类的构造方法。
	 */
	private MysqlConnectionPool() {
		// 加载配置文件
		loadConf();
		
		// 初始化连接对象并压入连接池
		initPool();
		
		// 启动定期校验(Ping)连接的独立线程
		if (MYSQL_VALIDATE_CONN_INTERVAL_TIME > 0 && mysqlConntPool.size() > 0) {
			new ValidateConnectionThread(MYSQL_VALIDATE_CONN_INTERVAL_TIME, mysqlConntPool).start();
		}
	}
	
	/**
	 * 加载配置文件内的信息。
	 */
	private void loadConf() {
		String SYSTEM_PATH_CHARACTOR = System.getProperty("file.separator");
		String strOSWebAppPath = CommonTool.getAbsolutWebAppPath(this.getClass(), SYSTEM_PATH_CHARACTOR);
		String strFilePathName = strOSWebAppPath + SYSTEM_PATH_CHARACTOR + "conf" + SYSTEM_PATH_CHARACTOR + "config.properties";
		
		Properties properties = new Properties();
		InputStream inputStream = null;
		try {
			inputStream = new FileInputStream(strFilePathName);
			properties.load(inputStream);
			inputStream.close(); // 关闭流`
			
			MYSQL_URL = properties.getProperty("URL");
			MYSQL_USER_NAME = properties.getProperty("user");
			MYSQL_USER_PASSWD = properties.getProperty("password");

			String strInitConn = properties.getProperty("init_conn");
			MYSQL_CONN_INITAL_SIZE = (strInitConn == null || "".equals(strInitConn)) ? 0 : Integer.parseInt(strInitConn);

			String strMaxConn = properties.getProperty("max_conn");
			MYSQL_CONN_MAX_SIZE = (strMaxConn == null || "".equals(strMaxConn)) ? 0 : Integer.parseInt(strMaxConn);
		
			String strIncRate = properties.getProperty("incremental_rate");
			MYSQL_CONN_INCREMENTAL_RATE = (strIncRate == null || "".equals(strIncRate)) ? 0.00f : Float.parseFloat(strIncRate);
			
			String strSleepTime = properties.getProperty("get_conn_sleep_time");
			MYSQL_GET_CONN_SLEEP_TIME = (strSleepTime == null || "".equals(strSleepTime)) ? 0L : Long.parseLong(strSleepTime);
			
			String strValidateTime = properties.getProperty("valid_conn_interval_time");
			MYSQL_VALIDATE_CONN_INTERVAL_TIME = (strValidateTime == null || "".equals(strValidateTime)) ? 0L : Long.parseLong(strValidateTime);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 加载连接资源并初始化一定数量的连接对象。
	 */
	private void initPool() {
		if (mysqlDs == null) {
			mysqlDs = new MysqlDataSource();
			mysqlDs.setURL(MYSQL_URL);
			mysqlDs.setUser(MYSQL_USER_NAME);
			mysqlDs.setPassword(MYSQL_USER_PASSWD);
			mysqlDs.setUseUnicode(true);
			mysqlDs.setEncoding("UTF-8");
			
			for(int i = 0; i < MYSQL_CONN_INITAL_SIZE; i++) {
				try {
					mysqlConntPool.put(mysqlDs.getConnection(), true);	// 此处的第二个参数(Boolean值)表示当前的连接(Connection)是否处于空闲状态。
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * 从连接池中获取对象。
	 * @param isAutoCommit
	 * @return
	 */
	public Connection getConnection(boolean isAutoCommit) {
		synchronized (SYNC_LOCK_OBJ) {
			Connection connt = null;

			if (mysqlConntPool.size() > 0) {
				Connection[] conns = mysqlConntPool.keySet().toArray(new Connection[0]);
				System.out.println("conns.length = " + conns.length);
				for(int i = 0; i < conns.length; i++) {
					Connection conn = conns[i];
					if (conn != null) {
						try {
							if (conn.isClosed() || !conn.isValid(5)) {	// isValid(5)方法，表示与服务端进行一次连接校验，并且校验的超时时间为5秒。
								mysqlConntPool.remove(conn);
								continue;
							}
							
							// 连接未关闭并且是有效连接
							boolean blnIsIdle = mysqlConntPool.get(conn);
							if (blnIsIdle) {	// 连接是空闲状态
								connt = conn;
								mysqlConntPool.put(conn, false);
								break;
							}								
						} catch (SQLException e) {
							e.printStackTrace();
						}
					}
				}
			}
			
			if (connt == null) {
				int intCurrSize = mysqlConntPool.size();
				System.out.println("intCurrSize = " + intCurrSize);
				
				int intNeedIncrementalValue = 0;
				if (intCurrSize < MYSQL_CONN_INITAL_SIZE) {
					intNeedIncrementalValue = MYSQL_CONN_INITAL_SIZE - intCurrSize;
				} else if (intCurrSize >= MYSQL_CONN_INITAL_SIZE && intCurrSize < MYSQL_CONN_MAX_SIZE) {
					int intAfterRate = (int) (intCurrSize * (1 + MYSQL_CONN_INCREMENTAL_RATE));
					if (intAfterRate > MYSQL_CONN_MAX_SIZE) {
						intNeedIncrementalValue = MYSQL_CONN_MAX_SIZE - intCurrSize;
					} else {
						intNeedIncrementalValue = intAfterRate - intCurrSize;
					}
				}
				
				System.out.println("intNeedIncrementalValue = " + intNeedIncrementalValue);
				
				for (int i = 0; i < intNeedIncrementalValue; i++) {
					try {
						mysqlConntPool.put(mysqlDs.getConnection(), true);
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
				
				try {
					Thread.sleep(MYSQL_GET_CONN_SLEEP_TIME);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				connt = getConnection(isAutoCommit);
			}
			
			if (connt != null) {
				try {
					connt.setAutoCommit(isAutoCommit);
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
			
			return connt;			
		}
	}
	
	/**
	 * 释放链接信息。
	 * @param conn
	 */
	public void releaseConnInfo(Connection conn) {
		releaseConnection(conn);
	}
	
	/**
	 * 释放链接信息。
	 * @param rs
	 * @param conn
	 */
	public void releaseConnInfo(PreparedStatement prst, Connection conn) {
		if (prst != null) {
			try {
				prst.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		releaseConnection(conn);
	}
	
	/**
	 * 数据回滚。
	 * @param conn
	 */
	public void rollback(Connection conn) {
		if (conn != null) {
			try {
				conn.rollback();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * 释放链接信息。
	 * @param rs
	 * @param prst
	 * @param conn
	 */
	public void releaseConnInfo(ResultSet rs, PreparedStatement prst, Connection conn) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		if (prst != null) {
			try {
				prst.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		releaseConnection(conn);
	}
	
	/**
	 * 释放连接池对象。
	 * @param conn
	 */
	private void releaseConnection(Connection conn) {
		if (conn == null) {
			return;
		}
		
		synchronized (SYNC_LOCK_OBJ) {
			try {
				if(mysqlConntPool.containsKey(conn)) {
					if (conn.isClosed()) {	// 当连接关闭
						mysqlConntPool.remove(conn);
					} else {
//						if (!conn.getAutoCommit()) {
//							//	Connection对象的AutoCommit值默认为True, 当前就是将此功能恢复为默认值。 只有用户从连接池中获取连接时，才由用户决定是否使用自动提交模式。
//							//	假设用户在释放Connection前，采用了事务模式(即：设置AutoCommit=false), 但是却没有执行commit()操作，若在此处执行setAutoCommit(true),则此方法将会帮助用户提交SQL的执行结果。
//							conn.setAutoCommit(true); 
//						}
						
						mysqlConntPool.put(conn, true);
					}
				} else {
					conn.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * 定期校验(Ping)连接的独立线程。
	 * @author xinwuhen
	 */
	private class ValidateConnectionThread extends Thread {
		private long lValidConnTime = 0;
		private Map<Connection, Boolean> mysqlConntPool = null;
		
		public ValidateConnectionThread (long lValidConnTime, Map<Connection, Boolean> mysqlConntPool) {
			this.lValidConnTime = lValidConnTime;
			this.mysqlConntPool = mysqlConntPool;
		}
		
		public void run() {
			if (lValidConnTime > 0) {
				while (true) {
					try {
						Thread.sleep(lValidConnTime * 1000);
						
						synchronized (SYNC_LOCK_OBJ) {
							if (mysqlConntPool != null) {
								System.out.println("mysqlConntPool.size = " + mysqlConntPool.size());
								
								Connection[] connKeys = mysqlConntPool.keySet().toArray(new Connection[0]);
								if (connKeys != null) {
									for (int i = 0; i < connKeys.length; i++) {
										Connection conn = connKeys[i];
										if (conn != null) {
											try {
												// 与MySQL服务器之间进行一次连接(应答超时时间设置为5秒)，并校验连接是否有效
												boolean blnIsValid = conn.isValid(5);
//												System.out.println("blnIsValid = " + blnIsValid);
												if (blnIsValid == false || conn.isClosed()) {
													mysqlConntPool.remove(conn);
												}
											} catch (SQLException e) {
												e.printStackTrace();
											}
										}
									}
								}
							}
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
}
