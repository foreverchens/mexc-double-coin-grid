package com.grid.mxc.common;/**
 *
 */

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @author yyy
 * @tg t.me/ychen5325
 */
public class SignTool {

	public static String sign(String content, String key) {
		try {
			Mac hmacSha256 = Mac.getInstance("HmacSHA256");
			SecretKeySpec secKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
			hmacSha256.init(secKey);
			byte[] hash = hmacSha256.doFinal(content.getBytes(StandardCharsets.UTF_8));
			return byte2hex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("No such algorithm: " + e.getMessage());
		} catch (InvalidKeyException e) {
			throw new RuntimeException("Invalid key: " + e.getMessage());
		}

	}

	private static String byte2hex(byte[] b) {
		StringBuilder sb = new StringBuilder();
		String tmp;
		for (int n = 0; b != null && n < b.length; n++) {
			tmp = Integer.toHexString(b[n] & 0XFF);
			if (tmp.length() == 1) {
				sb.append('0');
			}
			sb.append(tmp);
		}
		return sb.toString();
	}


	private static String urlEncode(String str) {
		try {
			return URLEncoder.encode(str, "UTF-8").replaceAll("\\+", "%20");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalArgumentException("UTF-8 encoding not supported!");
		}
	}

	public static String toQueryStr(Map<String, String> params) {
		return params.entrySet().stream().map((entry) -> entry.getKey() + "=" + urlEncode(entry.getValue())).collect(Collectors.joining("&"));
	}
}
