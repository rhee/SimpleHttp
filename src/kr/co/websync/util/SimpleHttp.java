package kr.co.websync.util;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

import org.apache.http.client.HttpClient;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;

public class SimpleHttp extends AsyncTask<String,Void,Throwable> {

	final static String TAG="SimpleHttp";

	static {
		//allow self-signed cert ( in fact, disables host name check )
		trustEveryone();
		disableConnectionReuseIfNecessary();// android-specific
	}

	private static class ConnectionThread extends HandlerThread
	{

		private static Object gLock = new Object();
		private static ConnectionThread gInstance;
		private static Handler gHandler;

		private ConnectionThread(String name) {
			super(name);
		}

		public static Handler getHandler()
		{
			synchronized(gLock){
				if(null==gHandler){
					if(null==gInstance){
						gInstance = new ConnectionThread("SimpleHttpConnection");
						gInstance.start();
					}
					gHandler=new Handler(gInstance.getLooper());
				}
				return gHandler;
			}
		}

	}

	private boolean do_post=false;
	private String http_url=null;	//request url
	private String[] http_params=null;
	private String http_file_key=null;
	private String http_file_name=null;
	private InputStream http_file_stream=null;
	private String http_params_built=""; //pre-built param string ( direct JSON send )

	private int http_status;
	private long http_server_time;
	private String http_response;

	private Runnable http_callback=null;
	private Looper originLooper;

	public interface SimpleHttpCallback {
		public abstract void onHttpOk(SimpleHttp task,int status,long server_time,String response);
		public abstract void onHttpError(SimpleHttp task,Throwable error,int status,String response);
	}

	public SimpleHttp(String url){
		super();
		this.http_url=url;
	}

	public SimpleHttp(String url,Context context){
		super();
		this.http_url=url;
	}

	public long getLastServerTime(){
		return http_server_time;
	}

	@Override
	protected Throwable doInBackground(String... requests){

		Thread.currentThread().setName("SimpleHttp"+SimpleHttp.incr());

		HttpURLConnection con = null;

		try {

			String url=http_url;

			// append parameters for GET method request
			if(!this.do_post) {
				if(this.http_params_built.length()>0)
					url+="?"+this.http_params_built;
			}

			con=(HttpURLConnection)(new URL(url)).openConnection();

			try{

				con.setInstanceFollowRedirects(true);

				if(this.do_post){

					con.setDoOutput(true);
					con.setRequestMethod("POST");

					if(this.http_file_key != null){

						final String boundaryString = "======"+(""+this.hashCode()+"_"+clock());

						con.setUseCaches(false);
						con.setChunkedStreamingMode(0);
						con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundaryString);

						DataOutputStream data = new DataOutputStream(con.getOutputStream());

						String[] params=this.http_params;

						if(this.http_params != null) {
							for(int i=0;i<params.length/2;i++){
								try{
									String k=params[2*i];
									byte[] v=params[2*i+1].getBytes("UTF-8");
									data.writeBytes("--" + boundaryString + "\r\n");
									data.writeBytes("Content-Disposition: form-data; name=\"" + k + "\"\r\n");
									data.writeBytes("\r\n");
									data.write(v);
									data.writeBytes("\r\n");
								}catch(UnsupportedEncodingException e) {
									Log.e(TAG,"Exception",e);
								}
							}
						}

						String filekey=this.http_file_key;
						String filename=this.http_file_name;
						InputStream input = this.http_file_stream;

						data.writeBytes("--" + boundaryString + "\r\n");
						data.writeBytes("Content-Disposition: form-data; name=\"" + filekey + "\"; filename=\"" + filename + "\"\r\n");
						data.writeBytes("Content-Type: text/plain\r\n");
						data.writeBytes("\r\n");

						int bytesAvailable = input.available();
						int maxBufferSize = 1024;
						int bufferSize = maxBufferSize;//Math.min(bytesAvailable, maxBufferSize);
						byte[] buffer = new byte[bufferSize];

						int bytesRead = input.read(buffer, 0, bufferSize);

						while (bytesRead > 0)
						{
							data.write(buffer, 0, bufferSize);

							bytesAvailable = input.available();
							bufferSize = Math.min(bytesAvailable, maxBufferSize);

							bytesRead = input.read(buffer, 0, bufferSize);
						}
						data.writeBytes("\r\n");

						data.writeBytes("--" + boundaryString + "--");
						data.writeBytes("\r\n");

						data.flush();
						data.close();

					} else {

						StringBuffer querysb = new StringBuffer(this.http_params_built);
						stringsAppendToSbUrlEncoded(querysb,this.http_params);

						con.setFixedLengthStreamingMode(querysb.toString().getBytes().length);
						con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
						PrintWriter out = new PrintWriter(con.getOutputStream());
						out.print(querysb);
						out.close();

					}
				}

				http_status=con.getResponseCode();
				http_server_time=con.getHeaderFieldDate("date",0);

				StringBuffer buf=new StringBuffer();
				BufferedReader in;

				if(http_status>=400) {
					in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
					Log.e(TAG,"HttpUrlConnection server error: "+http_status+" server: "+getConnectionServerAddr(con));
				} else {
					in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				}

				String line=null;
				while(null!=(line=in.readLine())){
					buf.append(line);//.append("\n");
				}
				in.close();

				http_response=buf.toString();

			}finally{
				con.disconnect();
			}

		}catch(MalformedURLException e){
			Log.e(TAG,"HttpUrlConnection error",e);
			return e;
		}catch(IOException e){
			Log.e(TAG,"HttpUrlConnection error: "+e+" server: "+getConnectionServerAddr(con));
			return e;
		}
		return null;

	}

	private static int _incr=0;

	private static int incr() {
		synchronized (SimpleHttp.class) {
			return ++_incr;
		}
	}

	@Override
	protected void onPostExecute(Throwable error){
		//		if(null==error){
		//			Log.v(TAG,""+http_server_time+","+http_url+","+http_status+","+http_response);
		//			//new Throwable().printStackTrace();
		//		}
		if(null!=http_callback){
			if(null==originLooper){
				http_callback.run();
			}else{
				new Handler(originLooper).post(http_callback);
				originLooper=null;
			}
		}
	}

	private void execute(String params_built,String[] params,String fileKey,String fileName,InputStream fileStream,final SimpleHttpCallback callback){

		this.http_params_built=params_built;
		this.http_params=params;
		this.http_file_key=fileKey;
		this.http_file_name=fileName;
		this.http_file_stream=fileStream;

		this.http_status=0;	//(re-)initalize
		this.http_server_time=0;
		this.http_response=null;

		this.http_callback=new Runnable(){
			@Override
			public void run() {
				if(null!=callback){

					if(200==http_status) {
						//						try {
						callback.onHttpOk(SimpleHttp.this,http_status,http_server_time,http_response);
						return;
						//						}catch(Exception e){
						//							Log.e(TAG,"Error: "+e.getMessage());
						//							callback.onHttpError(SimpleHttp.this,e,http_status,http_response);
						//							return;
						//						}
					}else{
						Throwable t = new RuntimeException("HTTP error status="+http_status);
						callback.onHttpError(SimpleHttp.this,t,http_status,http_response);
						return;
					}
				}
			}
		};
		//Log.d(LOG_TAG,"SimpleHttp.execute: url=\""+http_url+"\", params=\""+http_params_built+"\"");
		super.execute();
	}

	private void executeWrapper(final String params_built,final String[] params,final String fileKey,final String fileName,final InputStream fileStream,final SimpleHttpCallback callback)
	{
		if(Looper.myLooper()==Looper.getMainLooper()) {
			this.originLooper = Looper.myLooper();
			ConnectionThread.getHandler().post(new Runnable(){
				@Override
				public void run() {
					execute(params_built,params,fileKey,fileName,fileStream,callback);
				}});
		}else{
			execute(params_built,params,fileKey,fileName,fileStream,callback);
		}
	}

	public void get(String params,SimpleHttpCallback callback) /*throws IOException*/ {
		this.do_post=false;
		executeWrapper(params,null,null,null,null,callback);
	}

	public void get(String[]params,SimpleHttpCallback callback) /*throws IOException*/ {
		this.do_post=false;
		executeWrapper(stringsToStringUrlEncoded("",params),null,null,null,null,callback);
	}

	public void get(SimpleHttpCallback callback) /* throws IOException */ {
		this.do_post=false;
		executeWrapper("",null,null,null,null,callback);
	}

	public void post(SimpleHttpCallback callback) /*throws IOException*/ {
		this.do_post=true;
		executeWrapper("",null,null,null,null,callback);
	}

	public void post(String params,SimpleHttpCallback callback) /*throws IOException*/ {
		this.do_post=true;
		executeWrapper(params,null,null,null,null,callback);
	}

	public void post(String[]params,SimpleHttpCallback callback) /*throws IOException*/ {
		this.do_post=true;
		executeWrapper("",params,null,null,null,callback);
	}

	public void post(String fileKey,String fileName,InputStream fileStream,SimpleHttpCallback callback) /*throws IOException*/ {
		this.do_post=true;
		executeWrapper("",null,fileKey,fileName,fileStream,callback);
	}

	public void post(String[]params,String fileKey,String fileName,InputStream fileStream,SimpleHttpCallback callback) /*throws IOException*/ {
		this.do_post=true;
		executeWrapper("",params,fileKey,fileName,fileStream,callback);
	}

	//////////////////////////////////////////////////////

	private static String getConnectionServerAddr(HttpURLConnection con) {
		if (null != con) {
			try {
				URL url = con.getURL();
				Log.d(TAG,"final URL: "+url);
				String host = url.getHost();
				InetAddress address = InetAddress.getByName(host);
				String ip = address.getHostAddress();
				return ip;
			}catch(Exception e) { //SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessExeption 
				Log.d(TAG, "Exception: "+e);
			}
			//try {
			//	Field field;
			//	field = con.getClass().getDeclaredField("http");
			//	field.setAccessible(true);
			//	HttpClient http = (HttpClient) field.get(con);
			//	field = HttpClient.class.getDeclaredField("serverSocket");
			//	field.setAccessible(true);
			//	Socket socket = (Socket) field.get(http);
			//	return socket.getInetAddress().getHostAddress();
			//} catch (Exception e) { //SecurityException NoSuchFieldException IllegalArgumentException IllegalAccessException
			//	Log.d(TAG, "Exception: "+e);
			//}
		}
		return "";
	}


	//////////////////////////////////////////////////////
	//////////////////////////////////////////////////////
	//////////////////////////////////////////////////////

	private static int stringsAppendToSbUrlEncoded(StringBuffer sb,String[] params) {
		if(params != null) {
			for(int i=0;i<params.length/2;i++){
				try {
					String encoded=URLEncoder.encode(params[i*2+1],"UTF-8");
					if(sb.length()>0)
						sb.append('&');
					sb
					.append(params[i*2])
					.append('=')
					.append(encoded);
				}catch(UnsupportedEncodingException e){
					Log.e(TAG,"Exception",e);
				}catch(NullPointerException e){
					StringBuffer esb = new StringBuffer();
					esb.append("NullPoitnerException: params: ");
					esb.append(""+params[0]);
					for(int ii=1;ii<params.length;ii++){
						esb.append(", "+params[ii]);
					}
					Log.d(TAG,esb.toString());
				}
			}
		}
		return sb.length();
	}

	private static String stringsToStringUrlEncoded(String initial,String[] params){
		StringBuffer postbuf=new StringBuffer(initial);
		stringsAppendToSbUrlEncoded(postbuf,params);
		return postbuf.toString();
	}

	//////////////////////////////////////////////////////
	//////////////////////////////////////////////////////
	//////////////////////////////////////////////////////

	//android-specific: allow android specific http cache feature
	public static void enableHttpResponseCache(Context context) {
		try {
			long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
			File httpCacheDir = new File(context.getCacheDir(), "http");
			Class.forName("android.net.http.HttpResponseCache")
			.getMethod("install", File.class, long.class)
			.invoke(null, httpCacheDir, httpCacheSize);
		} catch (Exception httpResponseCacheNotAvailable) {
			/* ignore if not implemented */
		}
	}

	//////////////////////////////////////////////////////
	//////////////////////////////////////////////////////
	//////////////////////////////////////////////////////

	//android-specific: workaround for pre-froyo socket pool leak
	private static void disableConnectionReuseIfNecessary() {
		// HTTP connection reuse which was buggy pre-froyo
		if (Integer.parseInt(Build.VERSION.SDK) <= Build.VERSION_CODES.ECLAIR_MR1)// <Build.VERSION_CODES.FROYO)
		{
			System.setProperty("http.keepAlive", "false");
		}
	}

	//java-generic: allow self-signed cert ( in fact, disables host name check )
	private static void trustEveryone() {
		try {
			HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier(){
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}});
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new X509TrustManager[]{new X509TrustManager(){
				public void checkClientTrusted(X509Certificate[] chain,
						String authType) throws CertificateException {}
				public void checkServerTrusted(X509Certificate[] chain,
						String authType) throws CertificateException {}
				public X509Certificate[] getAcceptedIssuers() {
					return new X509Certificate[0];
				}}}, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(
					context.getSocketFactory());
		} catch (Exception e) { // should never happen
			e.printStackTrace();
		}
	}

	///////////////

	private static long clock()
	{
		//return (new java.util.Date()).getTime(); //generic java
		return SystemClock.uptimeMillis(); // android specific
	}

}

/* Example 1:

   import org.json.JSONObject;
   import org.json.JSONArray;
   import org.json.JSONException;

   try {

   String request=new JSONObject()
   .put("key","value")
   .put("list",new JSONArray()
   .put(new JSONObject()
   .put("k1","v1"))
   .put(new JSONObject()
   .put("k2","v2")))
   .toString();

   SimpleHttp h = new SimpleHttp("http://your.server.name/your/api/path/");
   h.post(request,new SimpleHttp.SimpleHttpCallback(){
   @Override
   public void onHttpOk(SimpleHttp task,int status,long server_time,String response){

   try {
   JSONObject json=new JSONObject(response);
   String result=json.getString("result");

// valid response. do use response/result

return; //success
}catch(JSONException e){
Log.e("TEST",e);
}

// do other thing on http ok but invalid resopnse

   }
   @Override
   public void onHttpError(SimpleHttp task,Throwable error,int status,String response){
   Log.d("TEST","onHttpError: "+status+" "+response);

// do something for http error

   }
   });

   }catch(JSONException e){
   Log.e(e);
   }

 */

/* Example 2:

   SimpleHttp h = new SimpleHttp("http://your.server.name/your/api/path/");
   h.post(new String[]{"key1","value1","key2","value2"},new SimpleHttp.SimpleHttpCallback(){
   @Override
   public void onHttpOk(SimpleHttp task,int status,long server_time,String response){
// check 'response' validity and do whatever
   }
   @Override
   public void onHttpError(SimpleHttp task,Throwable error,int status,String response){
   Log.d("TEST","onHttpError: "+status+" "+response);
// do something for http error
   }
   });

 */

