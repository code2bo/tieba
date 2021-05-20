package cn.imzjw.tieba.entity;

/**
 * @author https://blog.imzjw.cn
 * @date 2021/1/9 16:18 
 */
public class Cookie {

	private static final Cookie COOKIE = new Cookie();

	private String bduss;

	private Cookie() {
	}

	public static Cookie getInstance() {
		return COOKIE;
	}

	public String getBduss() {
		return bduss;
	}

	public void setBduss(String bduss) {
		this.bduss = bduss;
	}

	public String getCookie() {
		return "BDUSS=" + bduss;
	}
}
