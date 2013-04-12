# Simple HTTP get/post methods for java/android

## Usage:

    import kr.co.websync.net.SimpleHttp;

    ...

    new SimpleHttp("url").get(new String[]{"p",param1,"p2",param2},new SimpleHttp.SimpleHttpCallback(){
        public void onHttpOk(SimpleHttp task,int status,long server_time,String response){
            Log.d(TAG,"SimpleHttp.onHttpOk: "+status+", time: "+server_time+", resp: "+response);
        }
        public void onHttpError(SimpleHttp task,Throwable error,int status,String response){
            Log.d(TAG,"SimpleHttp.onHttpError: "+status+", resp: "+response);
        }
    });

    ...
