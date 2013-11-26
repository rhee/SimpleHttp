package kr.co.websync.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

public class AlarmHandler {

	private static final String TAG = "AlarmHandler";

	// shared BroadcastReceiver handling
	private static class DelayedPostManager {

		private static final String ALARM_EVENT = "kr.co.websync.util.AlarmHandler.DelayedPostManager.ALARM_EVENT";

		private static class DelayedPost implements Comparable<DelayedPost> {
			public AlarmHandler handler;
			public long time;
			public Runnable callback;

			@Override
			public int compareTo(DelayedPost arg0) {
				return (int)(this.time - arg0.time);
			}
		}

		final private PriorityQueue<DelayedPost> mPosts = new PriorityQueue<DelayedPost>();
		final private Map<Runnable,DelayedPost> mPostsMap = new HashMap<Runnable,DelayedPost>();

		private Context mContext;
		private AlarmManager mAlarmManager;
		private PendingIntent mBroadcast;
		private BroadcastReceiver mReceiver;

		private DelayedPostManager(Context context)
		{
			mContext = context;

			mAlarmManager = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
			mBroadcast = PendingIntent.getBroadcast(mContext,0,new Intent(ALARM_EVENT),0); //PendingIntent.FLAG_CANCEL_CURRENT);
			mReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					String action=intent.getAction();
					if(ALARM_EVENT.equals(action)){
						final long now = SystemClock.elapsedRealtime();

						synchronized(DelayedPostManager.this) {
							DelayedPost next;
							int count = 0;
							while ( null != (next = mPosts.peek()) && next.time <= now ) {
								next.handler.post(next.callback);
								mPosts.poll();
								mPostsMap.remove(next.callback);
								count++;
							}

							Log.d(TAG, "/// "+hashCode()+" ALARM_EVENT: handled "+count+", rest "+mPosts.size()+"/"+now);

							setNextAlarm();
						}
					}
				}
			};
			
			mContext.registerReceiver(mReceiver, new IntentFilter(ALARM_EVENT));
		}
		
		private static Map<Context,DelayedPostManager> gInstances = new HashMap<Context,DelayedPostManager>();

		public static DelayedPostManager getInstance(Context context)
		{
			DelayedPostManager instance = gInstances.get(context);
			if(null==instance){
				instance=new DelayedPostManager(context);
			}
			return instance;
		}

		public synchronized boolean addDelayedPost(AlarmHandler handler, Runnable r, long time) {
			//Log.d(TAG, "/// "+hashCode()+" addDelayedPost: "+time+"/"+SystemClock.elapsedRealtime());
			DelayedPost post = mPostsMap.get(r);
			
			if(null==post){
				post = new DelayedPost();
			}else{
				mPosts.remove(post);
				mPostsMap.remove(post);
			}
			
			post.handler = handler;
			post.callback = r;
			post.time = time;
			
			mPosts.offer(post);
			mPostsMap.put(post.callback, post);
			
			setNextAlarm();
			return true; 		//XXX TBD check loop is exiting or not
		}

		public synchronized void removeDelayedPost(AlarmHandler owner,Runnable r) {
			//Log.d(TAG, "/// "+hashCode()+" removeDelayedPost: "+r);
			mPosts.remove(mPostsMap.get(r));
			mPostsMap.remove(r);
			setNextAlarm();
		}

		public synchronized void removeDelayedPosts(AlarmHandler owner) {
			//Log.d(TAG, "/// "+hashCode()+" removeDelayedPosts");
			///NOTE: should check owner //mPosts.clear();
			{
				Iterator<DelayedPost> it = mPosts.iterator();
				while(it.hasNext()){
					if(it.next().handler == owner)
						it.remove();
				}
			}
			///NOTE: should check owner //mPostsMap.clear();
			{
				Iterator<Map.Entry<Runnable,DelayedPost>> it = mPostsMap.entrySet().iterator();
				while(it.hasNext()){
					if(it.next().getValue().handler == owner)
						it.remove();
				}
			}
			setNextAlarm();
		}

		private void setNextAlarm()
		{
			DelayedPost next = mPosts.peek();
			if(null == next) {
				//Log.d(TAG, "/// "+hashCode()+" setNextAlarm: count: "+mPosts.size());
			}else{
				long time = next.time;
				//Log.d(TAG, "/// "+hashCode()+" setNextAlarm: count: "+mPosts.size()+" "+time+"/"+SystemClock.elapsedRealtime());
				mAlarmManager.set(
						AlarmManager.ELAPSED_REALTIME_WAKEUP,
						time,
						mBroadcast);
			}
		}
		
	}

	private DelayedPostManager mManager;
	private Handler mHandler;
	
	public AlarmHandler(Context context,Looper looper)
	{
		mManager = DelayedPostManager.getInstance(context);
		mHandler = new Handler(looper);
	}

	public AlarmHandler(Context context)
	{
		this(context,context.getMainLooper());
	}
	
	public boolean post(Runnable callback)
	{
		return mHandler.post(callback);
	}
	
	public boolean postAtTime(Runnable callback,long time)
	{
		return mManager.addDelayedPost(this,callback,time);
	}

	public boolean postDelayed(Runnable callback,long delay)
	{
		long now = SystemClock.elapsedRealtime();
		long time = now + delay;
		return mManager.addDelayedPost(this,callback,time);
	}
	
	public void removeCallbacks(Runnable r)
	{
		mHandler.removeCallbacks(r);
		mManager.removeDelayedPost(this,r);
	}

	public void removeCallbacksAndMessages(Object token)
	{
		if(null != token){
			Log.e(TAG, "removeCallbacksAndMessages with token!=null not supported");
			return;
		}
		mHandler.removeCallbacksAndMessages(null);
		mManager.removeDelayedPosts(this);
	}

}
