package com.neighborhood.aka.laplace.estuary.mysql.schema.def.columndef;

import java.math.BigDecimal;

public class DecimalColumnDef extends ColumnDef {
	public DecimalColumnDef(String name, String type, int pos) {
		super(name, type, pos);
	}

	@Override
	public String toSQL(Object value) {
		BigDecimal d = (BigDecimal) value;

		return d.toEngineeringString();
	}
}
