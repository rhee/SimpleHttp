package kr.co.websync.util;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import android.util.Base64;

public class SimpleCompression {

	private static final String TAG="SimpleCompression";
	
	//import java.io.ByteArrayOutputStream;
	//import java.util.zip.Deflater;
	//import android.util.Base64;

	public static String makeCompressedString(String input){
		//return Base64.encodeToString(baos.toByteArray(),android.util.Base64.DEFAULT);
		//RFC3548 URL_SAFE?
		return Base64.encodeToString(
				makeCompressedBytes(input.getBytes()),
				android.util.Base64.NO_WRAP); //|Base64.URL_SAFE);
	}

	public static byte[] makeCompressedBytes(byte[] bytes){
		Deflater deflater = new Deflater();
		deflater.setInput(bytes);
		deflater.finish();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		while(!deflater.finished()){
			int byteCount=deflater.deflate(buf);
			baos.write(buf,0,byteCount);
		}
		deflater.end();
		return baos.toByteArray();
	}

	public static String parseCompressedString(int decompressedByteCount,byte[] compressedBytes) {
		
		//Log.d(TAG,"=== parseCompressedString: "+decompressedByteCount+", "+compressedBytes.length);

		String result="";
		Inflater inflater = new Inflater();
		inflater.setInput(compressedBytes, 0, compressedBytes.length);
		byte[] decompressedBytes = new byte[decompressedByteCount];
		try {
			if (inflater.inflate(decompressedBytes) != decompressedByteCount) {
				inflater.end();
				Log.e(TAG, "decompressedBytes mismatch");
				return result;
			}
		}catch(DataFormatException e){
			inflater.end();
			return result;
		}
		inflater.end();
		try {
			result = new String(decompressedBytes,"US-ASCII");
		}catch(UnsupportedEncodingException e){
			Log.e(TAG, "Exception", e);
		}
		return result;
	}

}
