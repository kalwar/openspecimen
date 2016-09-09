package com.krishagni.catissueplus.core.administrative.services.impl;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import com.krishagni.catissueplus.core.administrative.domain.StorageContainer;
import com.krishagni.catissueplus.core.administrative.events.ContainerSelectorCriteria;
import com.krishagni.catissueplus.core.administrative.events.TenantDetail;
import com.krishagni.catissueplus.core.administrative.services.ContainerSelectionStrategy;
import com.krishagni.catissueplus.core.biospecimen.domain.CollectionProtocol;
import com.krishagni.catissueplus.core.biospecimen.repository.DaoFactory;

@Configurable
public class LeastEmptyContainerSelectionStrategy implements ContainerSelectionStrategy {
	private StorageContainer lastSelected;

	private Map<String, StorageContainer> recentlySelectedContainers = new HashMap<>();

	@Autowired
	private DaoFactory daoFactory;

	@Override
	public StorageContainer getContainer(TenantDetail criteria) {
		if (isLastSelectedEligible(criteria)) {
			return lastSelected;
		}

		StorageContainer container = getRecentlySelected(criteria);
		if (container != null) {
			return (lastSelected = container);
		}


		long t1 = System.currentTimeMillis();
		Long containerId = daoFactory.getStorageContainerDao().getLeastEmptyContainerId(
			new ContainerSelectorCriteria()
				.cpId(criteria.getCpId())
				.specimenClass(criteria.getSpecimenClass())
				.type(criteria.getSpecimenType())
				.minFreePositions(1)
				.reservedLaterThan(ignoreReservationsBeforeDate()));
		if (containerId == null) {
			return null;
		}
		System.err.println("**** SQL execution time: " + (System.currentTimeMillis() - t1) + " ms");

		container = daoFactory.getStorageContainerDao().getById(containerId);
		recentlySelectedContainers.put(key(criteria), container);
		return (lastSelected = container);
	}

	private boolean isLastSelectedEligible(TenantDetail crit) {
		return isContainerEligible(lastSelected, crit);
	}

	private boolean isContainerEligible(StorageContainer container, TenantDetail crit) {
		if (container == null || !container.hasFreePositionsForReservation()) {
			return false;
		}

		CollectionProtocol cp = new CollectionProtocol();
		cp.setId(crit.getCpId());
		return container.canContainSpecimen(cp, crit.getSpecimenClass(), crit.getSpecimenType());
	}

	private StorageContainer getRecentlySelected(TenantDetail crit) {
		StorageContainer container = recentlySelectedContainers.get(key(crit));
		if (isContainerEligible(container, crit)) {
			return container;
		}

		return null;
	}

	private Date ignoreReservationsBeforeDate() {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.MINUTE, -5);
		return cal.getTime();
	}

	private String key(TenantDetail crit) {
		return crit.getCpId() + "-" + crit.getSpecimenClass() + "-" + crit.getSpecimenType();
	}
}