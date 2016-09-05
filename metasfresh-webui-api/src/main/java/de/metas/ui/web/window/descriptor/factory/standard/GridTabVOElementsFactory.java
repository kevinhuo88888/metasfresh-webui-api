package de.metas.ui.web.window.descriptor.factory.standard;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.adempiere.ad.expression.api.ConstantLogicExpression;
import org.adempiere.ad.expression.api.IExpressionFactory;
import org.adempiere.ad.expression.api.ILogicExpression;
import org.adempiere.ad.expression.api.IStringExpression;
import org.adempiere.mm.attributes.api.IAttributeDAO;
import org.adempiere.util.Services;
import org.compiere.model.GridFieldVO;
import org.compiere.model.GridTabVO;
import org.compiere.model.GridWindowVO;
import org.compiere.model.I_AD_UI_Column;
import org.compiere.model.I_AD_UI_Element;
import org.compiere.model.I_AD_UI_ElementField;
import org.compiere.model.I_AD_UI_ElementGroup;
import org.compiere.model.I_AD_UI_Section;
import org.compiere.model.MLookupInfo;
import org.compiere.util.DisplayType;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import de.metas.logging.LogManager;
import de.metas.ui.web.window.WindowConstants;
import de.metas.ui.web.window.datatypes.LookupValue.IntegerLookupValue;
import de.metas.ui.web.window.datatypes.LookupValue.StringLookupValue;
import de.metas.ui.web.window.descriptor.DocumentEntityDescriptor;
import de.metas.ui.web.window.descriptor.DocumentFieldDescriptor;
import de.metas.ui.web.window.descriptor.DocumentFieldWidgetType;
import de.metas.ui.web.window.descriptor.DocumentLayoutColumnDescriptor;
import de.metas.ui.web.window.descriptor.DocumentLayoutDetailDescriptor;
import de.metas.ui.web.window.descriptor.DocumentLayoutElementDescriptor;
import de.metas.ui.web.window.descriptor.DocumentLayoutElementFieldDescriptor;
import de.metas.ui.web.window.descriptor.DocumentLayoutElementGroupDescriptor;
import de.metas.ui.web.window.descriptor.DocumentLayoutElementLineDescriptor;
import de.metas.ui.web.window.descriptor.DocumentLayoutSectionDescriptor;
import de.metas.ui.web.window.descriptor.DocumentLayoutSideListDescriptor;
import de.metas.ui.web.window.descriptor.LayoutType;
import de.metas.ui.web.window.descriptor.sql.SqlDefaultValueExpression;
import de.metas.ui.web.window.descriptor.sql.SqlDocumentEntityDataBindingDescriptor;
import de.metas.ui.web.window.descriptor.sql.SqlDocumentFieldDataBindingDescriptor;
import de.metas.ui.web.window.exceptions.DocumentLayoutBuildException;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2016 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

/*package*/class GridTabVOElementsFactory
{
	// services
	private static final transient Logger logger = LogManager.getLogger(GridTabVOElementsFactory.class);
	private final transient IExpressionFactory expressionFactory = Services.get(IExpressionFactory.class);

	public static final int MAIN_TabNo = 0;
	/** Column names where we shall use {@link DocumentFieldWidgetType#Switch} instead of {@link DocumentFieldWidgetType#YesNo} */
	private static final Set<String> COLUMNNAMES_Switch = ImmutableSet.of(WindowConstants.FIELDNAME_IsActive); // FIXME: hardcoded

	/** Logic expression which evaluates as <code>true</code> when IsActive flag exists but it's <code>false</code> */
	private static final ILogicExpression LOGICEXPRESSION_NotActive;
	/** Logic expression which evaluates as <code>true</code> when Processed flag exists and it's <code>true</code> */
	private static final ILogicExpression LOGICEXPRESSION_Processed;
	private static final IStringExpression DEFAULT_VALUE_EXPRESSION_Yes;
	private static final IStringExpression DEFAULT_VALUE_EXPRESSION_No;
	private static final IStringExpression DEFAULT_VALUE_EXPRESSION_Zero;
	private static final IStringExpression DEFAULT_VALUE_EXPRESSION_M_AttributeSetInstance_ID;
	private static final IStringExpression DEFAULT_VALUE_EXPRESSION_NextLineNo;

	static
	{
		final IExpressionFactory expressionFactory = Services.get(IExpressionFactory.class);
		LOGICEXPRESSION_NotActive = expressionFactory.compile("@" + WindowConstants.FIELDNAME_IsActive + "/Y@=N", ILogicExpression.class);
		LOGICEXPRESSION_Processed = expressionFactory.compile("@" + WindowConstants.FIELDNAME_Processed + "/N@=Y | @" + WindowConstants.FIELDNAME_Processing + "/N@=Y", ILogicExpression.class);

		DEFAULT_VALUE_EXPRESSION_Yes = expressionFactory.compile(DisplayType.toBooleanString(true), IStringExpression.class);
		DEFAULT_VALUE_EXPRESSION_No = expressionFactory.compile(DisplayType.toBooleanString(false), IStringExpression.class);
		DEFAULT_VALUE_EXPRESSION_Zero = expressionFactory.compile("0", IStringExpression.class);
		DEFAULT_VALUE_EXPRESSION_M_AttributeSetInstance_ID = expressionFactory.compile(String.valueOf(IAttributeDAO.M_AttributeSetInstance_ID_None), IStringExpression.class);
		DEFAULT_VALUE_EXPRESSION_NextLineNo = expressionFactory.compile("@" + WindowConstants.CONTEXTVAR_NextLineNo + "@", IStringExpression.class);
	}

	//
	// Parameters
	private final GridWindowVO _gridWindowVO;
	private final GridTabVO _gridTabVO;
	private final GridTabVO _parentTab;
	
	//
	// Build parameters
	private final String _detailId;
	private IWindowUIElementsProvider _uiProvider;
	private final List<I_AD_UI_Section> _uiSections;
	private final ILogicExpression _tabReadonlyLogic;

	//
	// State
	private final SpecialFieldsCollector specialFieldsCollector = new SpecialFieldsCollector();
	//
	private DocumentEntityDescriptor.Builder _documentEntityBuilder;
	private SqlDocumentEntityDataBindingDescriptor.Builder _documentEntryDataBinding;
	private final Set<String> publicFieldNames = new HashSet<>();
	private final Set<String> advancedFieldNames = new HashSet<>();

	public GridTabVOElementsFactory(final GridWindowVO gridWindowVO, final GridTabVO gridTabVO, final GridTabVO parentTab)
	{
		super();
		_gridWindowVO = gridWindowVO;
		_gridTabVO = gridTabVO;
		_parentTab = parentTab;
		logger.trace("Creating factory for {}, parentTab={}", _gridTabVO, _parentTab);

		//
		// Detail ID
		{
			final int tabNo = _gridTabVO.getTabNo();
			_detailId = tabNo == MAIN_TabNo ? null : String.valueOf(tabNo);
			logger.trace("detailId={}", _detailId);
		}

		_tabReadonlyLogic = extractTabReadonlyLogic(gridTabVO);
		logger.trace("TabReadonlyLogic={}", _tabReadonlyLogic);

		//
		// Pick the right UI elements provider (DAO, fallback to InMemory),
		// and fetch the UI sections
		{
			IWindowUIElementsProvider uiProvider = new DAOWindowUIElementsProvider();
			final int AD_Tab_ID = gridTabVO.getAD_Tab_ID();
			List<I_AD_UI_Section> uiSections = uiProvider.getUISections(AD_Tab_ID);
			if (uiSections.isEmpty())
			{
				uiProvider = new InMemoryUIElementsProvider();
				logger.warn("No UI Sections found for {}. Switching to {}", gridTabVO, uiProvider);

				uiSections = uiProvider.getUISections(AD_Tab_ID);
			}

			_uiSections = ImmutableList.copyOf(uiSections);
			logger.trace("UI sections: {}", _uiSections);

			_uiProvider = uiProvider;
			logger.trace("Using UI provider: {}", _uiProvider);
		}
	}

	private IWindowUIElementsProvider getUIProvider()
	{
		return _uiProvider;
	}

	private final List<I_AD_UI_Section> getUISections()
	{
		return _uiSections;
	}

	private boolean isSOTrx()
	{
		return _gridWindowVO.isSOTrx();
	}

	private GridTabVO getParentGridTabVO()
	{
		return _parentTab;
	}

	private GridTabVO getGridTabVO()
	{
		return _gridTabVO;
	}

	private GridFieldVO getGridFieldVOById(final int adFieldId)
	{
		return getGridTabVO().getFieldByAD_Field_ID(adFieldId);
	}

	public SpecialFieldsCollector getSpecialFieldsCollector()
	{
		return specialFieldsCollector;
	}

	private String getDetailId()
	{
		return _detailId;
	}

	public List<DocumentLayoutSectionDescriptor.Builder> layoutSectionsList()
	{
		final List<I_AD_UI_Section> uiSections = getUISections();
		logger.trace("Generating layout sections list for {}", uiSections);
		
		//
		// UI Sections
		final List<DocumentLayoutSectionDescriptor.Builder> layoutSectionBuilders = new ArrayList<>();
		for (final I_AD_UI_Section uiSection : uiSections)
		{
			layoutSectionBuilders.add(layoutSection(uiSection));
		}

		return layoutSectionBuilders;
	}

	private DocumentLayoutSectionDescriptor.Builder layoutSection(final I_AD_UI_Section uiSection)
	{
		final DocumentLayoutSectionDescriptor.Builder layoutSectionBuilder = DocumentLayoutSectionDescriptor.builder()
				.setInternalName(uiSection.toString());

		if (!uiSection.isActive())
		{
			return layoutSectionBuilder.setInvalid("UI section not active: " + uiSection);
		}

		//
		// UI Columns
		for (final I_AD_UI_Column uiColumn : getUIProvider().getUIColumns(uiSection))
		{
			final DocumentLayoutColumnDescriptor.Builder layoutColumnBuilder = layoutColumn(uiColumn);
			if (layoutColumnBuilder == null)
			{
				continue;
			}
			layoutSectionBuilder.addColumn(layoutColumnBuilder);
		}

		return layoutSectionBuilder;
	}

	private DocumentLayoutColumnDescriptor.Builder layoutColumn(final I_AD_UI_Column uiColumn)
	{
		if (!uiColumn.isActive())
		{
			logger.trace("Skip adding {} because it's not active", uiColumn);
			return null;
		}

		final List<I_AD_UI_ElementGroup> uiElementGroups = getUIProvider().getUIElementGroups(uiColumn);
		logger.trace("Generating layout column for {}: {}", uiColumn, uiElementGroups);

		final DocumentLayoutColumnDescriptor.Builder layoutColumnBuilder = DocumentLayoutColumnDescriptor.builder()
				.setInternalName(uiColumn.toString());

		//
		// UI Element Groups
		for (final I_AD_UI_ElementGroup uiElementGroup : uiElementGroups)
		{
			final DocumentLayoutElementGroupDescriptor.Builder layoutElementGroupBuilder = layoutElementGroup(uiElementGroup);
			if (layoutElementGroupBuilder == null)
			{
				continue;
			}
			layoutColumnBuilder.addElementGroup(layoutElementGroupBuilder);
		}

		return layoutColumnBuilder;
	}

	private DocumentLayoutElementGroupDescriptor.Builder layoutElementGroup(final I_AD_UI_ElementGroup uiElementGroup)
	{
		if (!uiElementGroup.isActive())
		{
			logger.trace("Skip building layout element group for {} because it's not active", uiElementGroup);
			return null;
		}

		final List<I_AD_UI_Element> uiElements = getUIProvider().getUIElements(uiElementGroup);
		logger.trace("Building layout element group for {}: {}", uiElementGroup, uiElements);

		final DocumentLayoutElementGroupDescriptor.Builder layoutElementGroupBuilder = DocumentLayoutElementGroupDescriptor.builder()
				.setInternalName(uiElementGroup.toString())
				.setLayoutType(uiElementGroup.getUIStyle());

		//
		// UI Elements
		for (final I_AD_UI_Element uiElement : uiElements)
		{
			if (!uiElement.isDisplayed())
			{
				logger.trace("Skip {} because it's not displayed", uiElement);
				continue;
			}

			final DocumentLayoutElementLineDescriptor.Builder layoutElementLineBuilder = layoutElementLine(uiElement, layoutElementGroupBuilder);
			if (layoutElementLineBuilder == null)
			{
				continue;
			}

			layoutElementGroupBuilder.addElementLine(layoutElementLineBuilder);
		}

		logger.trace("Built layout element group for {}: {}", uiElementGroup, layoutElementGroupBuilder);
		return layoutElementGroupBuilder;
	}

	private DocumentLayoutElementLineDescriptor.Builder layoutElementLine(final I_AD_UI_Element uiElement, final DocumentLayoutElementGroupDescriptor.Builder layoutElementGroupBuilder)
	{
		logger.trace("Building layout element line for {}", uiElement);

		final DocumentLayoutElementDescriptor.Builder layoutElementBuilder = layoutElement(uiElement, layoutElementGroupBuilder);
		if (layoutElementBuilder == null)
		{
			logger.trace("Skip building layout element line because got null layout element", uiElement);
			return null;
		}

		final DocumentLayoutElementLineDescriptor.Builder layoutElementLineBuilder = DocumentLayoutElementLineDescriptor.builder()
				.setInternalName(uiElement.toString())
				.addElement(layoutElementBuilder);

		logger.trace("Built layout element line for {}: {}", uiElement, layoutElementLineBuilder);
		return layoutElementLineBuilder;
	}

	private DocumentLayoutElementDescriptor.Builder layoutElement(final I_AD_UI_Element uiElement)
	{
		final DocumentLayoutElementGroupDescriptor.Builder layoutElementGroupBuilder = null;
		return layoutElement(uiElement, layoutElementGroupBuilder);
	}

	private DocumentLayoutElementDescriptor.Builder layoutElement(final I_AD_UI_Element uiElement, @Nullable final DocumentLayoutElementGroupDescriptor.Builder layoutElementGroupBuilder)
	{
		logger.trace("Building layout element for {}", uiElement);

		if (!uiElement.isActive())
		{
			logger.trace("Skip building layout element for {} because it's not active", uiElement);
			return null;
		}

		LayoutType layoutType = LayoutType.fromNullable(uiElement.getUIStyle());
		if (layoutType == null && layoutElementGroupBuilder != null)
		{
			final boolean isFirstElementInGroup = !layoutElementGroupBuilder.hasElementLines();
			layoutType = isFirstElementInGroup ? LayoutType.primary : LayoutType.secondary;
		}

		//
		// UI main field
		final DocumentLayoutElementDescriptor.Builder layoutElementBuilder = DocumentLayoutElementDescriptor.builder()
				.setInternalName(uiElement.toString())
				.setCaption(uiElement.getName())
				.setDescription(uiElement.getDescription())
				.setLayoutType(layoutType)
				.setAdvancedField(uiElement.isAdvancedField());
		{
			final GridFieldVO gridFieldVO = getGridFieldVOById(uiElement.getAD_Field_ID());
			if (gridFieldVO != null)
			{
				layoutElementBuilder
						.setCaptionTrls(gridFieldVO.getHeaderTrls())
						.setDescriptionTrls(gridFieldVO.getDescriptionTrls())
						.setWidgetType(extractWidgetType(gridFieldVO))
						.addField(layoutElementField(gridFieldVO));

				specialFieldsCollector.collect(layoutElementBuilder);
			}
			else
			{
				logger.warn("No grid field found for {} (AD_Field_ID={})", uiElement, uiElement.getAD_Field_ID());
			}
		}

		//
		// UI Element Fields (if any)
		for (final I_AD_UI_ElementField uiElementField : getUIProvider().getUIElementFields(uiElement))
		{
			if (!uiElementField.isActive())
			{
				logger.trace("Skip {} because it's not active", uiElementField);
				continue;
			}

			final GridFieldVO gridFieldVO = getGridFieldVOById(uiElementField.getAD_Field_ID());
			if (gridFieldVO != null)
			{
				if (layoutElementBuilder.getWidgetType() == null)
				{
					layoutElementBuilder.setWidgetType(extractWidgetType(gridFieldVO));
				}
				final DocumentLayoutElementFieldDescriptor.Builder layoutElementFieldBuilder = layoutElementField(gridFieldVO);
				layoutElementBuilder.addField(layoutElementFieldBuilder);

				specialFieldsCollector.collect(layoutElementBuilder);
			}
			else
			{
				logger.warn("No grid field found for {} (AD_Field_ID={})", uiElementField, uiElementField.getAD_Field_ID());
			}
		}

		if (layoutElementBuilder.isAdvancedField())
		{
			advancedFieldNames.addAll(layoutElementBuilder.getFieldNames());
		}

		logger.trace("Built layout element for {}: {}", uiElement, layoutElementBuilder);
		return layoutElementBuilder;
	}

	public DocumentLayoutDetailDescriptor.Builder layoutDetail()
	{
		logger.trace("Generating layout detail");
		
		final GridTabVO detailTab = getGridTabVO();

		// If the detail is never displayed then don't add it to layout
		final ILogicExpression tabDisplayLogic = extractTabDisplayLogic();
		if (tabDisplayLogic.isConstantFalse())
		{
			logger.trace("Skip adding detail tab to layout because it's never displayed: {}, tabDisplayLogic={}", detailTab, tabDisplayLogic);
			return null;
		}

		final DocumentLayoutDetailDescriptor.Builder layoutDetailBuilder = DocumentLayoutDetailDescriptor.builder()
				.setDetailId(getDetailId())
				.setCaption(detailTab.getName())
				.setCaptionTrls(detailTab.getNameTrls())
				.setDescription(detailTab.getDescription())
				.setDescriptionTrls(detailTab.getDescriptionTrls());

		final List<I_AD_UI_Section> uiSections = getUISections();
		uiSections.stream()
				.flatMap(uiSection -> getUIProvider().getUIColumns(uiSection).stream())
				.flatMap(uiColumn -> getUIProvider().getUIElementGroups(uiColumn).stream())
				.flatMap(uiElementGroup -> getUIProvider().getUIElements(uiElementGroup).stream())
				.filter(uiElement -> uiElement.isDisplayedGrid())
				.sorted((uiElement1, uiElement2) -> uiElement1.getSeqNoGrid() - uiElement2.getSeqNoGrid())
				.map(uiElement -> layoutElement(uiElement))
				.filter(uiElement -> uiElement != null)
				.forEach(layoutDetailBuilder::addElement);

		return layoutDetailBuilder;
	}

	public DocumentEntityDescriptor.Builder documentEntity()
	{
		if (_documentEntityBuilder == null)
		{
			final GridTabVO gridTabVO = getGridTabVO();
			final ILogicExpression allowInsert = ConstantLogicExpression.of(gridTabVO.isInsertRecord());
			final ILogicExpression allowDelete = ConstantLogicExpression.of(gridTabVO.isDeleteable());
			final ILogicExpression readonly = getTabReadonlyLogic();

			final ILogicExpression allowCreateNewLogic = allowInsert.andNot(readonly);
			final ILogicExpression allowDeleteLogic = allowDelete.andNot(readonly);

			final ILogicExpression displayLogic = extractTabDisplayLogic();

			_documentEntityBuilder = DocumentEntityDescriptor.builder()
					.setDetailId(getDetailId())
					//
					.setAllowCreateNewLogic(allowCreateNewLogic)
					.setAllowDeleteLogic(allowDeleteLogic)
					.setDisplayLogic(displayLogic)
					//
					.setDataBinding(documentEntryDataBinding())
					//
					.setAD_Window_ID(gridTabVO.getAD_Window_ID()) // legacy
					.setAD_Tab_ID(gridTabVO.getAD_Tab_ID()) // legacy
					.setTabNo(gridTabVO.getTabNo()) // legacy
					.setIsSOTrx(isSOTrx()) // legacy
					;
		}
		return _documentEntityBuilder;
	}

	public SqlDocumentEntityDataBindingDescriptor.Builder documentEntryDataBinding()
	{
		if (_documentEntryDataBinding == null)
		{
			final GridTabVO tab = getGridTabVO();

			_documentEntryDataBinding = SqlDocumentEntityDataBindingDescriptor.builder()
					.setSqlTableName(tab.getTableName())
					.setSqlTableAliasFromDetailId(getDetailId())
					.setAD_Table_ID(tab.getAD_Table_ID()) // legacy
					.setSqlParentLinkColumnName(extractParentLinkColumnName())
					.setSqlWhereClause(tab.getWhereClause())
					.setSqlOrderBy(tab.getOrderByClause());
		}

		return _documentEntryDataBinding;
	}

	public DocumentFieldDescriptor.Builder documentField(
			final GridFieldVO gridFieldVO //
	)
	{
		// From entry data-binding:
		final SqlDocumentEntityDataBindingDescriptor.Builder entityBindingsBuilder = documentEntryDataBinding();
		final String sqlTableName = entityBindingsBuilder.getSqlTableName();
		final String sqlTableAlias = entityBindingsBuilder.getSqlTableAlias();
		final String sqlParentLinkColumnName = entityBindingsBuilder.getSqlParentLinkColumnName();

		// From GridTabVO:
		final String detailId = getDetailId();

		// From GridFieldVO:
		final String fieldName = gridFieldVO.getColumnName();
		final String sqlColumnName = fieldName;
		final boolean keyColumn = gridFieldVO.isKey();

		//
		final boolean isParentLinkColumn = sqlColumnName.equals(sqlParentLinkColumnName);

		//
		//
		final int displayType;
		final int AD_Reference_Value_ID;
		final int AD_Val_Rule_ID;
		final DocumentFieldWidgetType widgetType;
		final Class<?> valueClass;
		final IStringExpression defaultValueExpression;
		final boolean publicField;
		final ILogicExpression readonlyLogic;
		final boolean alwaysUpdateable;
		final ILogicExpression mandatoryLogic;
		final ILogicExpression displayLogic;
		final boolean advancedField = advancedFieldNames.contains(fieldName);
		final boolean sideListField = keyColumn || documentEntryDataBinding().isSideListFieldName(fieldName);
		if (isParentLinkColumn)
		{
			displayType = DisplayType.ID;
			AD_Reference_Value_ID = 0; // none
			AD_Val_Rule_ID = 0;
			widgetType = DocumentFieldWidgetType.Integer;
			valueClass = Integer.class;
			defaultValueExpression = IStringExpression.NULL;
			publicField = false;
			readonlyLogic = ILogicExpression.TRUE;
			alwaysUpdateable = false;
			mandatoryLogic = ILogicExpression.TRUE;
			displayLogic = ILogicExpression.FALSE;
		}
		else
		{
			displayType = gridFieldVO.getDisplayType();
			AD_Reference_Value_ID = gridFieldVO.getAD_Reference_Value_ID();
			AD_Val_Rule_ID = gridFieldVO.getAD_Val_Rule_ID();
			widgetType = extractWidgetType(gridFieldVO);
			valueClass = extractValueClass(gridFieldVO);
			defaultValueExpression = extractDefaultValueExpression(gridFieldVO);
			readonlyLogic = extractFieldReadonlyLogic(gridFieldVO);
			alwaysUpdateable = extractAlwaysUpdateable(gridFieldVO);
			final boolean presentInLayout = publicFieldNames.contains(fieldName);
			displayLogic = extractFieldDisplayLogic(gridFieldVO, presentInLayout);
			publicField = isPublicField(keyColumn, displayLogic);
			mandatoryLogic = extractMandatoryLogic(gridFieldVO, publicField);
		}

		//
		// ORDER BY SortNo
		int orderBySortNo = gridFieldVO.getSortNo();
		if (orderBySortNo == 0 && keyColumn)
		{
			orderBySortNo = Integer.MAX_VALUE;
		}

		final SqlDocumentFieldDataBindingDescriptor dataBinding = SqlDocumentFieldDataBindingDescriptor.builder()
				.setSqlTableName(sqlTableName)
				.setSqlTableAlias(sqlTableAlias)
				.setSqlColumnName(sqlColumnName)
				.setSqlColumnSql(gridFieldVO.getColumnSQL(false))
				.setAD_Column_ID(gridFieldVO.getAD_Column_ID()) // legacy
				.setDisplayType(displayType)
				.setAD_Reference_Value_ID(AD_Reference_Value_ID)
				.setAD_Val_Rule_ID(AD_Val_Rule_ID)
				.setKeyColumn(keyColumn)
				.setParentLinkColumn(isParentLinkColumn)
				.setEncrypted(gridFieldVO.isEncryptedColumn())
				.setOrderBy(orderBySortNo)
				.build();

		final DocumentFieldDescriptor.Builder fieldBuilder = DocumentFieldDescriptor.builder()
				.setFieldName(sqlColumnName)
				.setDetailId(detailId)
				//
				.setKey(keyColumn)
				.setParentLink(isParentLinkColumn)
				//
				.setWidgetType(widgetType)
				.setValueClass(valueClass)
				.setVirtualField(gridFieldVO.isVirtualColumn())
				.setCalculated(gridFieldVO.isCalculated())
				//
				.setDefaultValueExpression(defaultValueExpression)
				//
				.setPublicField(publicField)
				.setAdvancedField(advancedField)
				.setSideListField(sideListField)
				//
				.setReadonlyLogic(readonlyLogic)
				.setAlwaysUpdateable(alwaysUpdateable)
				.setMandatoryLogic(mandatoryLogic)
				.setDisplayLogic(displayLogic)
				//
				.setDataBinding(dataBinding);

		//
		// Add Field builder to document entity
		documentEntity().addField(fieldBuilder);

		//
		// Add Field's data binding to entity data binding
		entityBindingsBuilder.addField(dataBinding);

		return fieldBuilder;
	}

	private String extractParentLinkColumnName()
	{
		// If this is the master tab then there is no parent link
		final GridTabVO parentTabVO = getParentGridTabVO();
		if (parentTabVO == null)
		{
			return null;
		}

		final GridTabVO tabVO = getGridTabVO();

		//
		// Get configured parent link if any
		{
			final GridFieldVO parentLinkField = tabVO.getParentLinkField();
			if (parentLinkField != null)
			{
				return parentLinkField.getColumnName();
			}
		}

		//
		// Try linking by parent's key field
		{
			final GridFieldVO parentKeyField = parentTabVO.getKeyField();
			if (parentKeyField != null)
			{
				final GridFieldVO parentLinkField = tabVO.getFieldByColumnName(parentKeyField.getColumnName());
				if (parentLinkField != null)
				{
					return parentLinkField.getColumnName();
				}
			}
		}

		return null;
	}

	private static DocumentFieldWidgetType extractWidgetType(final GridFieldVO gridFieldVO)
	{
		final int displayType = gridFieldVO.getDisplayType();

		//
		//
		if (displayType == DisplayType.List)
		{
			return DocumentFieldWidgetType.List;
		}
		else if (displayType == DisplayType.Location)
		{
			return DocumentFieldWidgetType.Address;
		}
		else if (displayType == DisplayType.PAttribute)
		{
			return DocumentFieldWidgetType.ProductAttributes;
		}
		else if (displayType == DisplayType.Table)
		{
			return DocumentFieldWidgetType.List;
		}
		else if (displayType == DisplayType.TableDir)
		{
			return DocumentFieldWidgetType.List;
		}
		else if (displayType == DisplayType.Search)
		{
			return DocumentFieldWidgetType.Lookup;
		}
		else if (DisplayType.isAnyLookup(displayType))
		{
			return DocumentFieldWidgetType.Lookup;
		}
		else if (displayType == DisplayType.ID)
		{
			return DocumentFieldWidgetType.Integer;
		}
		//
		//
		else if (displayType == DisplayType.Date)
		{
			return DocumentFieldWidgetType.Date;
		}
		else if (displayType == DisplayType.Time)
		{
			return DocumentFieldWidgetType.Time;
		}
		else if (displayType == DisplayType.DateTime)
		{
			return DocumentFieldWidgetType.DateTime;
		}
		//
		//
		else if (displayType == DisplayType.TextLong || displayType == DisplayType.Memo || displayType == DisplayType.Text)
		{
			return DocumentFieldWidgetType.LongText;
		}
		else if (DisplayType.isText(displayType))
		{
			return DocumentFieldWidgetType.Text;
		}
		//
		//
		else if (DisplayType.Integer == displayType)
		{
			return DocumentFieldWidgetType.Integer;
		}
		else if (displayType == DisplayType.Amount)
		{
			return DocumentFieldWidgetType.Amount;
		}
		else if (displayType == DisplayType.Number)
		{
			return DocumentFieldWidgetType.Number;
		}
		else if (displayType == DisplayType.CostPrice)
		{
			return DocumentFieldWidgetType.CostPrice;
		}
		else if (displayType == DisplayType.Quantity)
		{
			return DocumentFieldWidgetType.Quantity;
		}
		//
		//
		else if (displayType == DisplayType.YesNo)
		{
			final String columnName = gridFieldVO.getColumnName();
			if (COLUMNNAMES_Switch.contains(columnName))
			{
				return DocumentFieldWidgetType.Switch;
			}
			return DocumentFieldWidgetType.YesNo;
		}
		else if (displayType == DisplayType.Button)
		{
			return DocumentFieldWidgetType.Button;
		}
		else if (displayType == DisplayType.Image)
		{
			return DocumentFieldWidgetType.Image;
		}
		//
		//
		else
		{
			throw new DocumentLayoutBuildException("Unknown displayType=" + displayType + " of " + gridFieldVO);
		}
	}

	private static Class<?> extractValueClass(final GridFieldVO gridFieldVO)
	{
		final int displayType = gridFieldVO.getDisplayType();

		//
		//
		if (displayType == DisplayType.List)
		{
			return StringLookupValue.class;
		}
		else if (displayType == DisplayType.Location)
		{
			return IntegerLookupValue.class;
		}
		else if (displayType == DisplayType.PAttribute)
		{
			return IntegerLookupValue.class;
		}
		else if (displayType == DisplayType.Table)
		{
			final MLookupInfo lookupInfo = gridFieldVO.getLookupInfo();
			final boolean numericKey = lookupInfo == null || lookupInfo.isNumericKey();
			return numericKey ? IntegerLookupValue.class : StringLookupValue.class;
		}
		else if (DisplayType.isAnyLookup(displayType))
		{
			return IntegerLookupValue.class;
		}
		else if (displayType == DisplayType.ID)
		{
			return Integer.class;
		}
		//
		//
		else if (displayType == DisplayType.Date)
		{
			return java.util.Date.class;
		}
		else if (displayType == DisplayType.Time)
		{
			return java.util.Date.class;
		}
		else if (displayType == DisplayType.DateTime)
		{
			return java.util.Date.class;
		}
		//
		//
		else if (displayType == DisplayType.TextLong || displayType == DisplayType.Memo || displayType == DisplayType.Text)
		{
			return String.class;
		}
		else if (DisplayType.isText(displayType))
		{
			return String.class;
		}
		//
		//
		else if (DisplayType.Integer == displayType)
		{
			return Integer.class;
		}
		else if (displayType == DisplayType.Amount)
		{
			return BigDecimal.class;
		}
		else if (displayType == DisplayType.Number)
		{
			return BigDecimal.class;
		}
		else if (displayType == DisplayType.CostPrice)
		{
			return BigDecimal.class;
		}
		else if (displayType == DisplayType.Quantity)
		{
			return BigDecimal.class;
		}
		//
		//
		else if (displayType == DisplayType.YesNo)
		{
			return Boolean.class;
		}
		else if (displayType == DisplayType.Button)
		{
			if (gridFieldVO.getAD_Reference_Value_ID() > 0)
			{
				return StringLookupValue.class;
			}
			return String.class;
		}
		else if (displayType == DisplayType.Image)
		{
			return Integer.class;
		}
		//
		//
		else
		{
			throw new DocumentLayoutBuildException("Unknown displayType=" + displayType + " of " + gridFieldVO);
		}
	}

	private static DocumentLayoutElementFieldDescriptor.LookupSource extractLookupSource(final GridFieldVO gridFieldVO)
	{
		final int displayType = gridFieldVO.getDisplayType();
		if (DisplayType.Search == displayType)
		{
			return DocumentLayoutElementFieldDescriptor.LookupSource.lookup;
		}
		else if (DisplayType.List == displayType)
		{
			return DocumentLayoutElementFieldDescriptor.LookupSource.list;
		}
		else if (DisplayType.TableDir == displayType)
		{
			return DocumentLayoutElementFieldDescriptor.LookupSource.list;
		}
		else if (DisplayType.Table == displayType)
		{
			return DocumentLayoutElementFieldDescriptor.LookupSource.list;
		}
		else if (DisplayType.isAnyLookup(displayType))
		{
			return DocumentLayoutElementFieldDescriptor.LookupSource.lookup;
		}
		else if (DisplayType.Button == displayType && gridFieldVO.getAD_Reference_Value_ID() > 0)
		{
			return DocumentLayoutElementFieldDescriptor.LookupSource.list;
		}
		else
		{
			return null;
		}
	}

	private static boolean isPublicField(final boolean isKey, final ILogicExpression displayLogic)
	{
		// Always publish the key columns, else the client won't know what to talk about ;)
		if (isKey)
		{
			return true;
		}

		// If display logic is not constant then we don't know if this field will be ever visible
		// so we are publishing it
		if (!displayLogic.isConstant())
		{
			return true;
		}

		// Publish this field only if it's displayed
		return displayLogic.constantValue() == true;
	}

	private ILogicExpression getTabReadonlyLogic()
	{
		return _tabReadonlyLogic;
	}

	private static ILogicExpression extractTabReadonlyLogic(final GridTabVO gridTabVO)
	{
		if (gridTabVO.isView())
		{
			return ILogicExpression.TRUE;
		}

		//
		// Check if tab is always readonly
		if (gridTabVO.isReadOnly())
		{
			return ILogicExpression.TRUE;
		}

		//
		// Check if tab's readonly expression
		final ILogicExpression tabReadonlyLogic = gridTabVO.getReadOnlyLogic();
		if (tabReadonlyLogic.isConstantTrue())
		{
			return ILogicExpression.TRUE;
		}

		return tabReadonlyLogic;
	}

	private ILogicExpression extractFieldReadonlyLogic(final GridFieldVO gridFieldVO)
	{
		// If the tab is always readonly, we can assume any field in that tab is readonly
		final ILogicExpression tabReadonlyLogic = getTabReadonlyLogic();
		if (tabReadonlyLogic.isConstantTrue())
		{
			return ILogicExpression.TRUE;
		}

		if (gridFieldVO.isReadOnly())
		{
			return ILogicExpression.TRUE;
		}

		if (gridFieldVO.isVirtualColumn())
		{
			return ILogicExpression.TRUE;
		}

		if (gridFieldVO.isKey())
		{
			return ILogicExpression.TRUE;
		}

		final String columnName = gridFieldVO.getColumnName();
		if (WindowConstants.FIELDNAMES_CreatedUpdated.contains(columnName))
		{
			// NOTE: from UI perspective those are readonly (i.e. it will be managed by persistence layer)
			return ILogicExpression.TRUE;
		}

		if (WindowConstants.FIELDNAME_DocStatus.equals(columnName))
		{
			// NOTE: DocStatus field shall always be readonly
			return ILogicExpression.TRUE;
		}

		final ILogicExpression fieldReadonlyLogic = gridFieldVO.getReadOnlyLogic();

		// FIXME: not sure if using tabReadonlyLogic here is OK, because the tab logic shall be applied to parent tab!
		ILogicExpression readonlyLogic = tabReadonlyLogic.or(fieldReadonlyLogic);

		//
		// Consider field readonly if the row is not active
		// .. and this property is not the IsActive flag.
		if (!WindowConstants.FIELDNAME_IsActive.equals(columnName))
		{
			readonlyLogic = LOGICEXPRESSION_NotActive.or(readonlyLogic);
		}

		//
		// Consider field readonly if the row is processed.
		// In case we deal with an AlwaysUpdateable field, this logic do not apply.
		final boolean alwaysUpdateable = extractAlwaysUpdateable(gridFieldVO);
		if (!alwaysUpdateable)
		{
			readonlyLogic = LOGICEXPRESSION_Processed.or(readonlyLogic);
		}

		return readonlyLogic;
	}

	private static boolean extractAlwaysUpdateable(final GridFieldVO gridFieldVO)
	{
		if (gridFieldVO.isVirtualColumn() || !gridFieldVO.isUpdateable())
		{
			return false;
		}
		return gridFieldVO.isAlwaysUpdateable();
	}

	private static ILogicExpression extractMandatoryLogic(final GridFieldVO gridFieldVO, final boolean publicField)
	{
		final String columnName = gridFieldVO.getColumnName();
		if (WindowConstants.FIELDNAMES_CreatedUpdated.contains(columnName))
		{
			// NOTE: from UI perspective those are not mandatory (i.e. it will be managed by persistence layer)
			return ILogicExpression.FALSE;
		}

		if (gridFieldVO.isVirtualColumn())
		{
			return ILogicExpression.FALSE;
		}

		// FIXME: hardcoded M_AttributeSetInstance_ID mandatory logic = false
		// Reason: even if we set it's default value to "0" some callouts are setting it to NULL,
		// and then the document saving API is failing because it considers this column as NOT filled.
		if (WindowConstants.FIELDNAME_M_AttributeSetInstance_ID.equals(columnName))
		{
			return ILogicExpression.FALSE;
		}

		// Corner case:
		// e.g. C_Order.M_Shipper_ID has AD_Field.IsMandatory=Y, AD_Field.IsDisplayed=N, AD_Column.IsMandatory=N
		// => we need to NOT enforce setting it because it's not needed, user cannot change it and it might be no callouts to set it.
		// Else, we won't be able to save our document.
		if (!publicField && gridFieldVO.isMandatory() && !gridFieldVO.isMandatoryDB())
		{
			return ILogicExpression.FALSE;
		}

		if (gridFieldVO.isMandatory())
		{
			return ILogicExpression.TRUE;
		}

		return gridFieldVO.getMandatoryLogic();
	}

	private ILogicExpression extractTabDisplayLogic()
	{
		return getGridTabVO().getDisplayLogic();
	}

	private ILogicExpression extractFieldDisplayLogic(final GridFieldVO gridFieldVO, final boolean presentInLayout)
	{
		if (!presentInLayout)
		{
			return ILogicExpression.FALSE;
		}

		// FIXME: not sure if using tabDisplayLogic here is OK, because the tab logic shall be applied to parent tab!
		final ILogicExpression tabDisplayLogic = extractTabDisplayLogic();
		final ILogicExpression fieldDisplayLogic = gridFieldVO.getDisplayLogic();
		return tabDisplayLogic.and(fieldDisplayLogic);
	}

	private IStringExpression extractDefaultValueExpression(final GridFieldVO gridFieldVO)
	{
		if (WindowConstants.FIELDNAME_Line.equals(gridFieldVO.getColumnName()))
		{
			return DEFAULT_VALUE_EXPRESSION_NextLineNo;
		}

		final String defaultValueStr = gridFieldVO.getDefaultValue();
		if (defaultValueStr == null || defaultValueStr.isEmpty())
		{
			final int displayType = gridFieldVO.getDisplayType();
			if (displayType == DisplayType.YesNo)
			{
				if (WindowConstants.FIELDNAME_IsActive.equals(gridFieldVO.getColumnName()))
				{
					return DEFAULT_VALUE_EXPRESSION_Yes;
				}
				else
				{
					return DEFAULT_VALUE_EXPRESSION_No;
				}
			}
			else if (DisplayType.isNumeric(displayType))
			{
				if (gridFieldVO.isMandatory())
				{
					// e.g. C_OrderLine.QtyReserved
					return DEFAULT_VALUE_EXPRESSION_Zero;
				}
			}
			else if (DisplayType.PAttribute == displayType)
			{
				return DEFAULT_VALUE_EXPRESSION_M_AttributeSetInstance_ID;
			}

			return IStringExpression.NULL;
		}
		else if (defaultValueStr.startsWith("@SQL="))
		{
			final String sqlTemplate = defaultValueStr.substring(5).trim();
			final IStringExpression sqlTemplateStringExpression = expressionFactory.compile(sqlTemplate, IStringExpression.class);
			return SqlDefaultValueExpression.of(sqlTemplateStringExpression);
		}
		else
		{
			final IStringExpression defaultValueExpression = expressionFactory.compile(defaultValueStr, IStringExpression.class);
			return defaultValueExpression;
		}
	}

	private final DocumentLayoutElementFieldDescriptor.Builder layoutElementField(final GridFieldVO gridFieldVO)
	{
		logger.trace("Building layout element field for {}", gridFieldVO);

		final String fieldName = gridFieldVO.getColumnName();
		final boolean presentInLayout = true;
		final ILogicExpression displayLogic = extractFieldDisplayLogic(gridFieldVO, presentInLayout);
		final boolean publicField = isPublicField(gridFieldVO.isKey(), displayLogic);

		if (publicField)
		{
			publicFieldNames.add(fieldName);
		}

		final DocumentLayoutElementFieldDescriptor.Builder layoutElementFieldBuilder = DocumentLayoutElementFieldDescriptor.builder(fieldName)
				.setInternalName(gridFieldVO.toString())
				.setLookupSource(extractLookupSource(gridFieldVO))
				.setPublicField(publicField);

		logger.trace("Built layout element field for {}: {}", gridFieldVO, layoutElementFieldBuilder);
		return layoutElementFieldBuilder;
	}

	public final DocumentLayoutSideListDescriptor layoutSideList()
	{
		final List<I_AD_UI_Section> uiSections = getUISections();
		logger.trace("Generating layout side list for {}", uiSections);
		
		final DocumentLayoutSideListDescriptor.Builder layoutSideListBuilder = DocumentLayoutSideListDescriptor.builder();

		uiSections.stream()
				.flatMap(uiSection -> getUIProvider().getUIColumns(uiSection).stream())
				.flatMap(uiColumn -> getUIProvider().getUIElementGroups(uiColumn).stream())
				.flatMap(uiElementGroup -> getUIProvider().getUIElements(uiElementGroup).stream())
				// .peek((uiElement)->System.out.println("UI ELEMENT: "+uiElement + ", SIDE="+uiElement.isDisplayed_SideList()))
				.filter(uiElement -> uiElement.isDisplayed_SideList())
				.sorted((uiElement1, uiElement2) -> uiElement1.getSeqNo_SideList() - uiElement2.getSeqNo_SideList())
				.map(uiElement -> layoutElement(uiElement))
				.filter(uiElement -> uiElement != null)
				.forEach(layoutSideListBuilder::addElement);

		documentEntryDataBinding().addSideListFieldNames(layoutSideListBuilder.getFieldNames());
		
		return layoutSideListBuilder.build();
	}
}