/*******************************************************************************
 * Copyright (c) 2013-2015 LAAS-CNRS (www.laas.fr)
 * 7 Colonel Roche 31077 Toulouse - France
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Thierry Monteil (Project co-founder) - Management and initial specification,
 *         conception and documentation.
 *     Mahdi Ben Alaya (Project co-founder) - Management and initial specification,
 *         conception, implementation, test and documentation.
 *     Christophe Chassot - Management and initial specification.
 *     Khalil Drira - Management and initial specification.
 *     Yassine Banouar - Initial specification, conception, implementation, test
 *         and documentation.
 ******************************************************************************/
package org.eclipse.om2m.comm.http;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.om2m.comm.service.RestClientService;
import org.eclipse.om2m.core.service.CseService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.util.tracker.ServiceTracker;

/**
 *  Manages the starting and stopping of the bundle.
 *  @author <ul>
 *            <li>Mahdi Ben Alaya < ben.alaya@laas.fr > < benalaya.mahdi@gmail.com ></li>
 *         <li>Yassine Banouar < ybanouar@laas.fr > < yassine.banouar@gmail.com ></li>
 *         </ul>
 */
public class Activator implements BundleActivator {
    /** Logger */
    private static Log LOGGER = LogFactory.getLog(Activator.class);
    /** HTTP service tracker */
    private ServiceTracker<Object, Object> httpServiceTracker;
    /** SCL service tracker */
    private ServiceTracker<Object, Object> cseServiceTracker;
    /** Sclbase listening context */
    private static final String CSE_BASE_CONTEXT = System.getProperty("org.eclipse.om2m.cseBaseContext","/om2m");

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        // Register the Rest HTTP Client
        LOGGER.info("Register HTTP RestClientService..");
        bundleContext.registerService(RestClientService.class.getName(), new RestHttpClient(), null);
        LOGGER.info("HTTP RestClientService is registered.");

        // track the HTTP service
        httpServiceTracker = new ServiceTracker<Object, Object>(bundleContext, HttpService.class.getName(), null) {
            public void removedService(ServiceReference<Object> reference, Object service) {
                LOGGER.info("HttpService removed");
                try {
                    LOGGER.info("Unregister "+CSE_BASE_CONTEXT+" http context");
                    ((HttpService) service).unregister(CSE_BASE_CONTEXT);
                } catch (IllegalArgumentException e) {
                    LOGGER.error("Error unregistring CseServlet",e);
                }
            }

            public Object addingService(ServiceReference<Object> reference) {
                LOGGER.info("HttpService discovered");
                HttpService httpService = (HttpService) this.context.getService(reference);
                try {
                    LOGGER.info("Register "+CSE_BASE_CONTEXT+" context");
                    httpService.registerServlet(CSE_BASE_CONTEXT, new RestHttpServlet(), null,null);
                } catch (Exception e) {
                    LOGGER.error("Error registering CseServlet",e);
                }
                return httpService;
            }
        };
        httpServiceTracker.open();

        // track the SCL service
        cseServiceTracker = new ServiceTracker<Object, Object>(bundleContext, CseService.class.getName(), null) {
            public void removedService(ServiceReference<Object> reference, Object service) {
                LOGGER.info("CseService removed");
                try {
                    RestHttpServlet.setCse((CseService) service);
                } catch (IllegalArgumentException e) {
                    LOGGER.error("Error removing CseService",e);
                }
            }

            public Object addingService(ServiceReference<Object> reference) {
                LOGGER.info("CseService discovered");
                CseService cse = (CseService) this.context.getService(reference);
                try {
                    RestHttpServlet.setCse(cse);
                } catch (Exception e) {
                    LOGGER.error("Error adding CseService",e);

                }
                return cse;
            }
        };
        // Open service trackers
        cseServiceTracker.open();
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
    }

}
