package com.krishagni.catissueplus.core.administrative.services.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.krishagni.catissueplus.core.administrative.services.ContainerSelectionStrategy;
import com.krishagni.catissueplus.core.administrative.services.ContainerSelectionStrategyFactory;

public class ContainerSelectionStrategyFactoryImpl implements ContainerSelectionStrategyFactory {
	private Log logger = LogFactory.getLog(ContainerSelectionStrategyFactoryImpl.class);

	private Map<String, Class> strategyClasses = new HashMap<>();

	@Override
	public ContainerSelectionStrategy getStrategy(String name) {
		try {
			Class klass = strategyClasses.get(name);
			return (ContainerSelectionStrategy)klass.newInstance();
		} catch (Exception e) {
			throw new RuntimeException("Error instantiating class", e);
		}

	}

	public void setStrategyClasses(Map<String, String> strategyClassNames) {
		strategyClassNames.forEach(
			(strategy, impl) -> {
				try {
					Class klass = Class.forName(impl);
					if (!ContainerSelectionStrategy.class.isAssignableFrom(klass)) {
						logger.error("Invalid class implementation " + impl + " for strategy " + strategy);
						return;
					}

					strategyClasses.put(strategy, klass);
				} catch (Exception e) {
					logger.error("Couldn't register class implementation " + impl + " for strategy " + strategy, e);
				}
			}
		);
	}
}