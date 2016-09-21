package com.krishagni.catissueplus.core.administrative.services.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import com.krishagni.catissueplus.core.administrative.domain.StorageContainer;
import com.krishagni.catissueplus.core.administrative.events.TenantDetail;
import com.krishagni.catissueplus.core.administrative.services.ContainerSelectionStrategy;
import com.krishagni.catissueplus.core.biospecimen.domain.CollectionProtocol;
import com.krishagni.catissueplus.core.biospecimen.repository.DaoFactory;

@Configurable
public class RecentlyUsedContainerSelectionStrategy implements ContainerSelectionStrategy {
	@Autowired
	private DaoFactory daoFactory;

	@Autowired
	private SessionFactory sessionFactory;

	private CollectionProtocol cp;

	private StorageContainer recentlyUsed = null;

	@Override
	public StorageContainer getContainer(TenantDetail criteria, Boolean aliquotsInSameContainer) {
		int numFreeLocs = 1;
		if (aliquotsInSameContainer != null && aliquotsInSameContainer && criteria.getNumOfAliquots() > 1) {
			numFreeLocs = criteria.getNumOfAliquots();
		}

		StorageContainer container = recentlyUsed;
		if (container == null) {
			container = getRecentlySelectedContainer(criteria);
		}

		if (container == null) {
			return null;
		}

		if (!canContainSpecimen(container, criteria, numFreeLocs)) {
			container = nextContainer(container.getParentContainer(), container, criteria, numFreeLocs, new HashSet<>());
		}

		return (recentlyUsed = container);
	}

	@SuppressWarnings("unchecked")
	private StorageContainer getRecentlySelectedContainer(TenantDetail crit) {
		List<StorageContainer> containers = getRecentlySelectedContainerQuery(crit)
			.add(Restrictions.eq("spmn.specimenClass", crit.getSpecimenClass()))
			.add(Restrictions.eq("spmn.specimenType", crit.getSpecimenType()))
			.list();

		if (CollectionUtils.isNotEmpty(containers)) {
			return containers.iterator().next();
		}

		containers = getRecentlySelectedContainerQuery(crit).list();
		return CollectionUtils.isNotEmpty(containers) ? containers.iterator().next() : null;
	}

	private Criteria getRecentlySelectedContainerQuery(TenantDetail criteria) {
		Session session = sessionFactory.getCurrentSession();
		session.enableFilter("activeEntity");
		return session.createCriteria(StorageContainer.class)
			.createAlias("occupiedPositions", "pos")
			.createAlias("pos.occupyingSpecimen", "spmn")
			.createAlias("spmn.visit", "visit")
			.createAlias("visit.registration", "reg")
			.createAlias("reg.collectionProtocol", "cp")
			.add(Restrictions.eq("cp.id", criteria.getCpId()))
			.addOrder(Order.desc("pos.id"))
			.setMaxResults(1);
	}

	private StorageContainer nextContainer(StorageContainer parent, StorageContainer last, TenantDetail criteria, int freeLocs, Set<StorageContainer> visited) {
		if (parent == null) {
			return null;
		}

		System.err.println("**** RU: Exploring children of: " + parent.getName() + ": " + parent.getType().getName() + ": " + ((last != null) ? last.getName() : "none"));

		int childIdx = -1;
		List<StorageContainer> children = parent.getChildContainersSortedByPosition();
//			.stream().filter(c -> !visited.contains(c))
//			.collect(Collectors.toList());

		if (last != null) {
			for (StorageContainer container : children) {
				childIdx++;

				if (container.getPosition().getPosition() == last.getPosition().getPosition()) {
					System.err.println("**** RU: Found " + container.getName() + " at " + childIdx);
					break;
				}
			}
		}

		for (int i = childIdx + 1; i < children.size(); ++i) {
			if (visited.add(children.get(i))) {
				StorageContainer container = nextContainer(children.get(i), null, criteria, freeLocs, visited);
				if (container != null) {
					System.err.println("**** RU: Selected " + container.getName());
					return container;
				}
			}
		}

		for (int i = 0; i < (childIdx + 1); ++i) {
			if (visited.add(children.get(i))) {
				StorageContainer container = nextContainer(children.get(i), null, criteria, freeLocs, visited);
				if (container != null) {
					System.err.println("**** RU: Selected " + container.getName());
					return container;
				}
			}
		}

		System.err.println("**** RU: Probing parent " + parent.getName());
		if (canContainSpecimen(parent, criteria, freeLocs)) {
			System.err.println("**** RU: Selected " + parent.getName());
			return parent;
		}

		if (visited.add(parent)) {
			System.err.println("**** RU: Visited " + parent.getName());
			return nextContainer(parent.getParentContainer(), parent, criteria, freeLocs, visited);
		}

		return null;
	}

	private boolean canContainSpecimen(StorageContainer container, TenantDetail crit, int freeLocs) {
		if (cp == null) {
			cp = daoFactory.getCollectionProtocolDao().getById(crit.getCpId());
		}

		return container.canContainSpecimen(cp, crit.getSpecimenClass(), crit.getSpecimenType()) &&
				container.freePositionsCount() >= freeLocs;
	}
}
