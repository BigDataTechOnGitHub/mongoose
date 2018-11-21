package com.emc.mongoose.control;

import com.emc.mongoose.Node;
import com.emc.mongoose.env.DateUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.DateFormat;

/**
 @author veronika K. on 08.11.18 */
public class RunServlet
	extends HttpServlet {

	private static final String CONTEXT_SEP = "/";
	private static final int STATUS_OK = 200;
	private static final int STATUS_ERROR = 400;
	private final Node node;

	public RunServlet(final Node node) {
		this.node = node;
	}

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
	throws ServletException, IOException {
		resp.addHeader("Node state", node.state().name());
		final DateFormat dtf = DateUtil.FMT_DATE_ISO8601;
		resp.addHeader("Node start time", dtf.format(node.startTime()));
		resp.setStatus(STATUS_OK);
		//TODO: add content
	}

	@Override
	protected void doPut(final HttpServletRequest req, final HttpServletResponse resp)
	throws ServletException, IOException {
		//TODO: start node
		//node.run();
	}
}