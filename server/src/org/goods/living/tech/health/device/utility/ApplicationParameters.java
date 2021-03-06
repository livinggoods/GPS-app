package org.goods.living.tech.health.device.utility;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 * This class reads application initialization values from context.xml
 * 
 * Best practice to keep configuration parameters outside the Web Application
 * i.e. neither in any class nor web.xml nor in resources directory
 * 
 * changes in the context.xml still requires restarting the Tomcat
 * but no need to re-compile the web application, 
 *     no need to re-test the web application
 *     no need to re-deploy the web application
 *     
 * example:
 * <!-- Application Parameters -->
 * <Parameter name="sg.edu.ntu.medicine.lkc.cephas.jwt.token.life" value="3600"/>
 */

@Named
@ApplicationScoped
public class ApplicationParameters {

	Logger logger = LogManager.getLogger();

	public ApplicationParameters() {
		logger.debug("ApplicationParameters instance is created");
	}

	@Inject
	private ServletContext servletContext;

	public String getTokenLife() {
		return servletContext.getInitParameter("org.goods.living.tech.health.device.jwt.token.life");
	}

	public String getMailHostname() {
		return servletContext.getInitParameter("org.goods.living.tech.health.device.mail.hostname");
	}

	public Integer getMailPort() {
		return Integer.parseInt(servletContext.getInitParameter("org.goods.living.tech.health.device.mail.port"));
	}

	public String getMailAuthenticator() {
		return servletContext.getInitParameter("org.goods.living.tech.health.device.mail.authenticator");
	}

	public String getMailPassword() {
		return servletContext.getInitParameter("org.goods.living.tech.health.device.mail.password");
	}

	public Boolean getTlsStatus() {
		return Boolean.valueOf(servletContext.getInitParameter("org.goods.living.tech.health.device.mail.tls.allow"));
	}

	public String getUrl() {
		return servletContext.getInitParameter("org.goods.living.tech.health.url");
	}

	public String getHashKey() {
		return servletContext.getInitParameter("org.goods.living.tech.health.device.jwt.token.hash.key");
	}

	public int getServerApi() {
		int temp = 1;
		try {
			temp = Integer.parseInt(servletContext.getInitParameter("org.goods.living.tech.health.device.api"));
		} catch (NumberFormatException exp) {
			logger.error(exp);
		}
		logger.debug("The temp variable value: " + temp);
		return temp;
	}

	public int getLocationUpdateInterval() {
		int temp = 300;
		try {
			temp = Integer.parseInt(
					servletContext.getInitParameter("org.goods.living.tech.health.device.locationUpdateInterval"));
		} catch (NumberFormatException exp) {
			logger.error(exp);
		}
		logger.debug("The temp variable value: " + temp);
		return temp;
	}

	public String getUSSDBalanceCode(String network) {

		try {
			String s = servletContext
					.getInitParameter("org.goods.living.tech.health.device.USSDBalanceCodes-" + network);
			logger.debug(s);
			return s;
		} catch (Exception e) {
			logger.error(e);
			return null;
		}

	}

	public Boolean shouldUpdateUSSDBalanceCodes() {

		try {
			String s = servletContext.getInitParameter("org.goods.living.tech.health.device.updateUSSDBalanceCodes");
			logger.debug(s);
			return new Boolean(s);
		} catch (Exception e) {
			logger.error(e);
			return false;
		}

	}

	public String getDataBalanceCheckTime() {

		try {
			String s = servletContext.getInitParameter("org.goods.living.tech.health.device.dataBalanceCheckTime");
			logger.debug(s);
			return s;
		} catch (Exception e) {
			logger.error(e);
			return null;
		}

	}

	public Boolean getDisableDatabalanceCheck() {

		try {
			String s = servletContext.getInitParameter("org.goods.living.tech.health.device.disableDatabalanceCheck");
			logger.debug(s);
			return new Boolean(s);
		} catch (Exception e) {
			logger.error(e);
			return null;
		}

	}

	public String getEmail() {

		try {
			String s = servletContext.getInitParameter("org.goods.living.tech.health.device.email.username");
			logger.debug(s);
			return s;
		} catch (Exception e) {
			logger.error(e);
			return null;
		}

	}

	public String getEmailPassword() {

		try {
			String s = servletContext.getInitParameter("org.goods.living.tech.health.device.email.password");
			logger.debug(s);
			return s;
		} catch (Exception e) {
			logger.error(e);
			return null;
		}

	}

	public String getEmailHostName() {

		try {
			String s = servletContext.getInitParameter("org.goods.living.tech.health.device.email.hostname");
			logger.debug(s);
			return s;
		} catch (Exception e) {
			logger.error(e);
			return null;
		}

	}

}
