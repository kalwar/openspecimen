package com.krishagni.catissueplus.core.administrative.events;

import java.util.ArrayList;
import java.util.List;

public class ReservePositionsOp {

	private Long cpId;

	private List<TenantDetail> tenants = new ArrayList<>();

	public Long getCpId() {
		return cpId;
	}

	public void setCpId(Long cpId) {
		this.cpId = cpId;
	}

	public List<TenantDetail> getTenants() {
		return tenants;
	}

	public void setTenants(List<TenantDetail> tenants) {
		this.tenants = tenants;
	}
}
