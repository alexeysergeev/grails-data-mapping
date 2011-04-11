/* Copyright (C) 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugin.mongo

import org.codehaus.groovy.grails.support.PersistenceContextInterceptor
import org.grails.datastore.gorm.support.DatastorePersistenceContextInterceptor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor

/**
 * Works around the issue where Grails only finds the first PersistenceContextInterceptor by
 * replacing all discovered interceptors with a single aggregating instance.
 *
 * @author Burt Beckwith
 */
class PersistenceContextInterceptorAggregator implements BeanDefinitionRegistryPostProcessor {

    private boolean hibernate
    private boolean mongo
    private boolean redis
    private boolean aggregate
    private List<PersistenceContextInterceptor> interceptors = []

    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {

        int count = 0
        if (registry.containsBeanDefinition('persistenceInterceptor')) {
            count++
            hibernate = true
        }
        if (registry.containsBeanDefinition('mongoPersistenceInterceptor')) {
            count++
            mongo = true
        }
        if (registry.containsBeanDefinition('redisDatastorePersistenceInterceptor')) {
            count++
            redis = true
        }

        if (count < 2) {
            return
        }

        aggregate = true

        if (registry.containsBeanDefinition('persistenceInterceptor')) {
            registry.removeBeanDefinition 'persistenceInterceptor'
        }

        if (registry.containsBeanDefinition('mongoPersistenceInterceptor')) {
            registry.removeBeanDefinition 'mongoPersistenceInterceptor'
        }

        if (registry.containsBeanDefinition('redisDatastorePersistenceInterceptor')) {
            registry.removeBeanDefinition 'redisDatastorePersistenceInterceptor'
        }
    }

    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (!aggregate) {
            return
        }

        if (hibernate) {
            def HibernatePersistenceContextInterceptor = Class.forName(
                'org.codehaus.groovy.grails.orm.hibernate.support.HibernatePersistenceContextInterceptor',
                true, Thread.currentThread().contextClassLoader)
            def interceptor = HibernatePersistenceContextInterceptor.newInstance()
            interceptor.sessionFactory = beanFactory.getBean('sessionFactory')
            interceptors << interceptor
        }

        if (mongo) {
            interceptors << new DatastorePersistenceContextInterceptor(beanFactory.getBean('mongoDatastore'))
        }

        if (redis) {
            interceptors << new DatastorePersistenceContextInterceptor(beanFactory.getBean('redisDatastore'))
        }

        beanFactory.registerSingleton('persistenceInterceptor',
                new AggregatePersistenceContextInterceptor(interceptors))
    }
}
