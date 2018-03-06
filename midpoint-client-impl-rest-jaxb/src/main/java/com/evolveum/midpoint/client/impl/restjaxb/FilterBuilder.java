package com.evolveum.midpoint.client.impl.restjaxb;

import java.util.List;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.client.api.*;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Element;

import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.prism.xml.ns._public.query_3.FilterClauseType;
import com.evolveum.prism.xml.ns._public.query_3.NAryLogicalOperatorFilterClauseType;
import com.evolveum.prism.xml.ns._public.query_3.OrderDirectionType;
import com.evolveum.prism.xml.ns._public.query_3.OrgFilterClauseType;
import com.evolveum.prism.xml.ns._public.query_3.PagingType;
import com.evolveum.prism.xml.ns._public.query_3.QueryType;
import com.evolveum.prism.xml.ns._public.query_3.SearchFilterType;
import com.evolveum.prism.xml.ns._public.types_3.ItemPathType;

public class FilterBuilder<O extends ObjectType> implements FilterEntryOrEmpty<O>, AtomicFilterExit<O> {
	
	private NAryLogicalOperatorFilterClauseType currentFilter;
	private FilterLogicalSymbol lastLogicalSymbol;
	
	private RestJaxbService service;
	private Class<O> type;
	
	private PagingType paging;
	
	public FilterBuilder(RestJaxbService service, Class<O> type) {
		this.service = service;
		this.type = type;
		this.currentFilter = new NAryLogicalOperatorFilterClauseType();
		lastLogicalSymbol = null;
			
	}
	
	private FilterBuilder(RestJaxbService service, Class<O> type, NAryLogicalOperatorFilterClauseType currentFilter, FilterLogicalSymbol logicalSymbol, PagingType paging) {
		this.service = service;
		this.type = type;
		this.currentFilter = currentFilter;
		this.lastLogicalSymbol = logicalSymbol;
		this.paging = paging;
	}

	
	public FilterBuilder<O> addSubfilter(Element subfilter, boolean negated) {
        if (!currentFilter.getFilterClause().isEmpty() && lastLogicalSymbol == null) {
            throw new IllegalStateException("lastLogicalSymbol is empty but there is already some filter present: " + currentFilter);
        }
            NAryLogicalOperatorFilterClauseType newFilter = appendAtomicFilter(subfilter, negated, lastLogicalSymbol);
            return new FilterBuilder<>(service, type, newFilter, null, paging);
    }
	
	private NAryLogicalOperatorFilterClauseType appendAtomicFilter(Element subfilter, boolean negated, FilterLogicalSymbol lastLogicalSymbol) {
		 DomSerializer dom = service.getDomSerializer(); 
		if (negated) {
			subfilter = dom.createNotFilter(subfilter);
//	            subfilter = null;// TODO: dom.createNotFilter()
	        }
		 
		 NAryLogicalOperatorFilterClauseType updatedFilter = new NAryLogicalOperatorFilterClauseType();
		 updatedFilter.getFilterClause().addAll(currentFilter.getFilterClause());
		 updatedFilter.setMatching(currentFilter.getMatching());
		 
		
		 
	        if (lastLogicalSymbol == null || lastLogicalSymbol == FilterLogicalSymbol.OR) {
	        	updatedFilter.getFilterClause().add(dom.createAndFilter(subfilter));
	        } else if (lastLogicalSymbol == FilterLogicalSymbol.AND) {
	            Element andFilter = (getLastCondition(updatedFilter));
	            dom.addCondition(andFilter, subfilter);
	        } else {
	            throw new IllegalStateException("Unknown logical symbol: " + lastLogicalSymbol);
	        }
	        return updatedFilter;
		
		
	}
	
	public Element getLastCondition(NAryLogicalOperatorFilterClauseType updatedFilter) {
		List<Element> conditions = updatedFilter.getFilterClause();
		if (conditions.isEmpty()) {
			return null;
		} else {
			return conditions.get(conditions.size()-1);
		}
	}


	@Override
	public SearchService<O> build() {
		QueryType queryType = new QueryType();
		queryType.setFilter(buildFilter());
		
		if (paging != null) {
			queryType.setPaging(paging);
		}
		return new RestJaxbSearchService<>(service, type, queryType);
		
	}


	@Override
	public ConditionEntry<O> item(ItemPathType itemPath) {
		return RestJaxbQueryBuilder.create(service, type, this, itemPath); //.item(itemPath);
	}


	@Override
	public ConditionEntry<O> item(QName... qnames) {
		String path = "";
		for (QName name : qnames) {
			path += "name" + "/";
		}
		ItemPathType itemPath = new ItemPathType();
		itemPath.setValue(StringUtils.removeEnd(path, "/"));
		return RestJaxbQueryBuilder.create(service, type, this, itemPath); //.item(qnames);
	}

	//TODO: Maybe we can re-structure interfaces to exclude some of the duplicated methods like build and paging
//	@Override
//	public PagingRuleBuilder<O> paging()
//	{
//		return null;
//	}

	@Override
	public FilterEntry<O> and() {
		return setLastLogicalSymbol(FilterLogicalSymbol.AND);
	}

	@Override
	public FilterEntry<O> or() {
		return setLastLogicalSymbol(FilterLogicalSymbol.OR);
	}

	private FilterBuilder<O> setLastLogicalSymbol(FilterLogicalSymbol newLogicalSymbol) {
		if (this.lastLogicalSymbol != null) {
            throw new IllegalStateException("Two logical symbols in a sequence");
        }
        return new FilterBuilder<O>(service, type, currentFilter, newLogicalSymbol, paging);
	}


//	@Override
//	public QueryBuilder<O> finishQuery() {
//		QueryType queryType = new QueryType();
//		queryType.setFilter(buildFilter());
//		return new RestJaxbQueryBuilder<>(service, type, queryType);
//	}
	
	public SearchFilterType buildFilter() {
		SearchFilterType filter = new SearchFilterType();
		if (currentFilter.getFilterClause().size() == 1) {
			Element firstFilter = currentFilter.getFilterClause().iterator().next();
			if (firstFilter.getTagName().equals("and")) {
				if (firstFilter.getChildNodes() != null && firstFilter.getChildNodes().getLength() == 1) {
					filter.setFilterClause((Element) firstFilter.getFirstChild());
				} else {
					filter.setFilterClause(firstFilter);
				}
			} else {
				filter.setFilterClause(firstFilter);
			}
		} else {
			Element orFilter = service.getDomSerializer().createOrFilter(currentFilter.getFilterClause());
			filter.setFilterClause(orFilter);
		}
		return filter;
	}

@Override
public AtomicFilterEntry<O> not() {
	// TODO Auto-generated method stub
	return null;
}

@Override
public AtomicFilterExit<O> endBlock() {
	// TODO Auto-generated method stub
	return null;
}

@Override
public FilterExit<O> asc(QName... names) {

	String pathValue = "";
	for (QName qname : names) {
		pathValue += qname.getLocalPart() + "/";
	}
	ItemPathType path = new ItemPathType();
	
	path.setValue(StringUtils.removeEnd(pathValue, "/"));;
	return addOrdering(path, OrderDirectionType.ASCENDING);
	
}

@Override
public FilterExit<O> asc(ItemPathType path) {
	return addOrdering(path, OrderDirectionType.ASCENDING);
}

@Override
public FilterExit<O> desc(QName... names) {
	// TODO Auto-generated method stub
	return null;
}

@Override
public FilterExit<O> desc(ItemPathType path) {
	return addOrdering(path, OrderDirectionType.DESCENDING);
}

@Override
public FilterExit<O> group(QName... names) {
	// TODO Auto-generated method stub
	return null;
}

@Override
public FilterExit<O> group(ItemPathType path) {
	return addGrouping(path);
}

@Override
public FilterExit<O> offset(Integer n) {
	return setOffset(n);
}

@Override
public FilterExit<O> maxSize(Integer n) {
	return setMaxSize(n);
}

private FilterBuilder<O> addOrdering(ItemPathType orderBy, OrderDirectionType direction) {
	paging = getPaging();
	paging.setOrderDirection(direction);
	paging.setOrderBy(orderBy);

	return new FilterBuilder<>(service, type, currentFilter, null, paging);
}

private FilterBuilder<O> addGrouping(ItemPathType groupBy) {
	paging = getPaging();
	paging.setGroupBy(groupBy);

	return new FilterBuilder<>(service, type, currentFilter, null, paging);
}

private FilterBuilder<O> setOffset(Integer n) {
	paging = getPaging();
	paging.setOffset(n);
	
	return new FilterBuilder<>(service, type, currentFilter, null, paging);
}

private FilterBuilder<O> setMaxSize(Integer n) {
	paging = getPaging();
	paging.setMaxSize(n);
	
	return new FilterBuilder<>(service, type, currentFilter, null, paging);
}

private PagingType getPaging() {
	if (paging == null) {
		paging = new PagingType();
	}
	
	return paging;
}


}
