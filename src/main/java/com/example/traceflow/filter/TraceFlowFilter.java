package com.example.traceflow.filter;

import com.example.traceflow.context.TraceContext;
import jakarta.servlet.*;

import java.io.IOException;

public class TraceFlowFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        try {
            chain.doFilter(request, response);
        } finally {
            TraceContext.flush();
        }
    }
}