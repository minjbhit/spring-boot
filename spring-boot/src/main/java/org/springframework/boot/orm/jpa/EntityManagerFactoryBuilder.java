/*
 * Copyright 2012-2016 the original author or authors.
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

package org.springframework.boot.orm.jpa;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.ApplicationInfo;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitManager;
import org.springframework.util.ClassUtils;
import org.springframework.util.ResourceUtils;

/**
 * Convenient builder for JPA EntityManagerFactory instances. Collects common
 * configuration when constructed and then allows you to create one or more
 * {@link LocalContainerEntityManagerFactoryBean} through a fluent builder pattern. The
 * most common options are covered in the builder, but you can always manipulate the
 * product of the builder if you need more control, before returning it from a
 * {@code @Bean} definition.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @since 1.3.0
 */
public class EntityManagerFactoryBuilder {

	private static final Log logger = LogFactory
			.getLog(EntityManagerFactoryBuilder.class);

	private JpaVendorAdapter jpaVendorAdapter;

	private PersistenceUnitManager persistenceUnitManager;

	private Map<String, Object> jpaProperties;

	private EntityManagerFactoryBeanCallback callback;

	private Class<?> applicationClass;

	/**
	 * Create a new instance passing in the common pieces that will be shared if multiple
	 * EntityManagerFactory instances are created.
	 * @param jpaVendorAdapter a vendor adapter
	 * @param jpaProperties JPA properties to be passed to the persistence provider.
	 * @param persistenceUnitManager optional source of persistence unit information (can
	 * be null)
	 */
	public EntityManagerFactoryBuilder(JpaVendorAdapter jpaVendorAdapter,
			Map<String, ?> jpaProperties, PersistenceUnitManager persistenceUnitManager) {
		this.jpaVendorAdapter = jpaVendorAdapter;
		this.persistenceUnitManager = persistenceUnitManager;
		this.jpaProperties = new LinkedHashMap<String, Object>(jpaProperties);
	}

	public Builder dataSource(DataSource dataSource) {
		return new Builder(dataSource);
	}

	/**
	 * An optional callback for new entity manager factory beans.
	 * @param callback the entity manager factory bean callback
	 */
	public void setCallback(EntityManagerFactoryBeanCallback callback) {
		this.callback = callback;
	}

	/**
	 * An optional {@link ApplicationInfo} used to further tune the entity manager.
	 * @param applicationInfo the application info
	 */
	public void setApplicationInfo(ApplicationInfo applicationInfo) {
		this.applicationClass = applicationInfo.getMainApplicationClass();
	}

	/**
	 * Determine a persistence unit root location to use if no {@code persistence.xml} or
	 * {@code orm.xml} are present in the project.
	 * @return the persistence unit root location or {@code null}
	 */
	protected String determinePersistenceUnitRootLocation() {
		if (this.applicationClass != null) {
			try {
				URL mainLocation = this.applicationClass.getProtectionDomain()
						.getCodeSource().getLocation();
				return ResourceUtils.extractJarFileURL(mainLocation).toString();
			}
			catch (Exception ex) {
				logger.info("Could not determine persistence unit root location: " + ex);
			}
		}
		return null;
	}

	/**
	 * A fluent builder for a LocalContainerEntityManagerFactoryBean.
	 */
	public final class Builder {

		private DataSource dataSource;

		private String[] packagesToScan;

		private String persistenceUnit;

		private Map<String, Object> properties = new HashMap<String, Object>();

		private boolean jta;

		private Builder(DataSource dataSource) {
			this.dataSource = dataSource;
		}

		/**
		 * The names of packages to scan for {@code @Entity} annotations.
		 * @param packagesToScan packages to scan
		 * @return the builder for fluent usage
		 */
		public Builder packages(String... packagesToScan) {
			this.packagesToScan = packagesToScan;
			return this;
		}

		/**
		 * The classes whose packages should be scanned for {@code @Entity} annotations.
		 * @param basePackageClasses the classes to use
		 * @return the builder for fluent usage
		 */
		public Builder packages(Class<?>... basePackageClasses) {
			Set<String> packages = new HashSet<String>();
			for (Class<?> type : basePackageClasses) {
				packages.add(ClassUtils.getPackageName(type));
			}
			this.packagesToScan = packages.toArray(new String[0]);
			return this;
		}

		/**
		 * The name of the persistence unit. If only building one EntityManagerFactory you
		 * can omit this, but if there are more than one in the same application you
		 * should give them distinct names.
		 * @param persistenceUnit the name of the persistence unit
		 * @return the builder for fluent usage
		 */
		public Builder persistenceUnit(String persistenceUnit) {
			this.persistenceUnit = persistenceUnit;
			return this;
		}

		/**
		 * Generic properties for standard JPA or vendor-specific configuration. These
		 * properties override any values provided in the constructor.
		 * @param properties the properties to use
		 * @return the builder for fluent usage
		 */
		public Builder properties(Map<String, ?> properties) {
			this.properties.putAll(properties);
			return this;
		}

		/**
		 * Configure if using a JTA {@link DataSource}, i.e. if
		 * {@link LocalContainerEntityManagerFactoryBean#setDataSource(DataSource)
		 * setDataSource} or
		 * {@link LocalContainerEntityManagerFactoryBean#setJtaDataSource(DataSource)
		 * setJtaDataSource} should be called on the
		 * {@link LocalContainerEntityManagerFactoryBean}.
		 * @param jta if the data source is JTA
		 * @return the builder for fluent usage
		 */
		public Builder jta(boolean jta) {
			this.jta = jta;
			return this;
		}

		public LocalContainerEntityManagerFactoryBean build() {
			LocalContainerEntityManagerFactoryBean entityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
			if (EntityManagerFactoryBuilder.this.persistenceUnitManager != null) {
				entityManagerFactoryBean.setPersistenceUnitManager(
						EntityManagerFactoryBuilder.this.persistenceUnitManager);
			}
			if (this.persistenceUnit != null) {
				entityManagerFactoryBean.setPersistenceUnitName(this.persistenceUnit);
			}
			entityManagerFactoryBean.setJpaVendorAdapter(
					EntityManagerFactoryBuilder.this.jpaVendorAdapter);

			if (this.jta) {
				entityManagerFactoryBean.setJtaDataSource(this.dataSource);
			}
			else {
				entityManagerFactoryBean.setDataSource(this.dataSource);
			}
			entityManagerFactoryBean.setPackagesToScan(this.packagesToScan);
			entityManagerFactoryBean.getJpaPropertyMap()
					.putAll(EntityManagerFactoryBuilder.this.jpaProperties);
			entityManagerFactoryBean.getJpaPropertyMap().putAll(this.properties);
			String rootLocation = determinePersistenceUnitRootLocation();
			if (rootLocation != null) {
				entityManagerFactoryBean.setPersistenceUnitRootLocation(rootLocation);
			}
			if (EntityManagerFactoryBuilder.this.callback != null) {
				EntityManagerFactoryBuilder.this.callback
						.execute(entityManagerFactoryBean);
			}
			return entityManagerFactoryBean;
		}

	}

	/**
	 * A callback for new entity manager factory beans created by a Builder.
	 */
	public interface EntityManagerFactoryBeanCallback {

		void execute(LocalContainerEntityManagerFactoryBean factory);

	}

}
