package de.metas.ui.web.document.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import groovy.transform.Immutable;
import lombok.NonNull;
import lombok.ToString;

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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

@Immutable
@ToString
public final class ImmutableDocumentFilterDescriptorsProvider implements DocumentFilterDescriptorsProvider
{
	public static final ImmutableDocumentFilterDescriptorsProvider of(final List<DocumentFilterDescriptor> descriptors)
	{
		if (descriptors == null || descriptors.isEmpty())
		{
			return EMPTY;
		}
		return new ImmutableDocumentFilterDescriptorsProvider(descriptors);
	}

	public static final ImmutableDocumentFilterDescriptorsProvider of(final DocumentFilterDescriptor descriptor)
	{
		if (descriptor == null)
		{
			return EMPTY;
		}
		return new ImmutableDocumentFilterDescriptorsProvider(ImmutableList.of(descriptor));
	}

	public static final ImmutableDocumentFilterDescriptorsProvider of(final DocumentFilterDescriptor... descriptors)
	{
		if (descriptors == null || descriptors.length == 0)
		{
			return EMPTY;
		}
		return new ImmutableDocumentFilterDescriptorsProvider(Arrays.asList(descriptors));
	}

	public static final Builder builder()
	{
		return new Builder();
	}

	public static final Collector<DocumentFilterDescriptor, ?, ImmutableDocumentFilterDescriptorsProvider> collector()
	{
		final Supplier<List<DocumentFilterDescriptor>> supplier = ArrayList::new;
		final BiConsumer<List<DocumentFilterDescriptor>, DocumentFilterDescriptor> accumulator = (list, filter) -> list.add(filter);
		final BinaryOperator<List<DocumentFilterDescriptor>> combiner = (list1, list2) -> {
			list1.addAll(list2);
			return list1;
		};
		final Function<List<DocumentFilterDescriptor>, ImmutableDocumentFilterDescriptorsProvider> finisher = ImmutableDocumentFilterDescriptorsProvider::of;

		return Collector.of(supplier, accumulator, combiner, finisher);
	}

	private static final ImmutableDocumentFilterDescriptorsProvider EMPTY = new ImmutableDocumentFilterDescriptorsProvider(ImmutableList.of());

	private final Map<String, DocumentFilterDescriptor> descriptorsByFilterId;

	private ImmutableDocumentFilterDescriptorsProvider(final List<DocumentFilterDescriptor> descriptors)
	{
		super();
		descriptorsByFilterId = Maps.uniqueIndex(descriptors, descriptor -> descriptor.getFilterId());
	}

	@Override
	public Collection<DocumentFilterDescriptor> getAll()
	{
		return descriptorsByFilterId.values();
	}

	@Override
	public DocumentFilterDescriptor getByFilterIdOrNull(final String filterId) throws NoSuchElementException
	{
		final DocumentFilterDescriptor descriptor = descriptorsByFilterId.get(filterId);
		return descriptor;
	}

	public static class Builder
	{
		private final List<DocumentFilterDescriptor> descriptors = new ArrayList<>();

		private Builder()
		{
		}

		public ImmutableDocumentFilterDescriptorsProvider build()
		{
			if (descriptors.isEmpty())
			{
				return EMPTY;
			}
			return new ImmutableDocumentFilterDescriptorsProvider(descriptors);
		}

		public Builder addDescriptor(@NonNull final DocumentFilterDescriptor descriptor)
		{
			descriptors.add(descriptor);
			return this;
		}

		public Builder addDescriptors(@NonNull final Collection<DocumentFilterDescriptor> descriptors)
		{
			if (descriptors.isEmpty())
			{
				return this;
			}

			this.descriptors.addAll(descriptors);
			return this;
		}

		public Builder addDescriptors(@NonNull final DocumentFilterDescriptorsProvider provider)
		{
			addDescriptors(provider.getAll());
			return this;
		}

	}

}
