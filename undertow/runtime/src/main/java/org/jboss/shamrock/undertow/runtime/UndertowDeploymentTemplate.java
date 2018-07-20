package org.jboss.shamrock.undertow.runtime;

import java.io.Closeable;
import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.jboss.shamrock.runtime.ContextObject;
import org.jboss.shamrock.runtime.InjectionInstance;
import org.jboss.shamrock.runtime.StartupContext;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;

/**
 * Provides the runtime methods to bootstrap Undertow. This class is present in the final uber-jar,
 * and is invoked from generated bytecode
 */
public class UndertowDeploymentTemplate {

    @ContextObject("deploymentInfo")
    public DeploymentInfo createDeployment(String name) {
        DeploymentInfo d = new DeploymentInfo();
        d.setClassLoader(getClass().getClassLoader());
        d.setDeploymentName(name);
        d.setContextPath("/");
        ClassLoader cl = UndertowDeploymentTemplate.class.getClassLoader();
        d.setClassLoader(cl);
        //should be fixed in Graal RC5
        //d.setResourceManager(new ClassPathResourceManager(d.getClassLoader(), "META-INF/resources"));
        return d;
    }

    public <T> InstanceFactory<T> createInstanceFactory(InjectionInstance<T> injectionInstance) {
        return new ShamrockInstanceFactory<T>(injectionInstance);
    }

    public void registerServlet(@ContextObject("deploymentInfo") DeploymentInfo info, String name, Class<?> servletClass, boolean asyncSupported, InstanceFactory<? extends Servlet> instanceFactory) throws Exception {
        ServletInfo servletInfo = new ServletInfo(name, (Class<? extends Servlet>) servletClass, instanceFactory);
        servletInfo.setLoadOnStartup(1);
        info.addServlet(servletInfo);
        servletInfo.setAsyncSupported(asyncSupported);
    }

    public void addServletMapping(@ContextObject("deploymentInfo") DeploymentInfo info, String name, String mapping) throws Exception {
        ServletInfo sv = info.getServlets().get(name);
        sv.addMapping(mapping);
    }

    public void addServletContextParameter(@ContextObject("deploymentInfo") DeploymentInfo info, String name, String value) {
        info.addInitParameter(name, value);
    }

    public void deploy(StartupContext startupContext, @ContextObject("servletHandler") HttpHandler handler) throws ServletException {
        Undertow val = Undertow.builder()
                .addHttpListener(8080, "localhost")
                .setHandler(handler)
                .build();
        val.start();
        startupContext.addCloseable(new Closeable() {
            @Override
            public void close() throws IOException {
                val.stop();
            }
        });
    }

    @ContextObject("servletHandler")
    public HttpHandler bootServletContainer(@ContextObject("deploymentInfo") DeploymentInfo info) {
        try {
            ServletContainer servletContainer = Servlets.defaultContainer();
            DeploymentManager manager = servletContainer.addDeployment(info);
            manager.deploy();
            return manager.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
