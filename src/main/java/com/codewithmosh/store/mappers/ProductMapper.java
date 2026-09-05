package com.codewithmosh.store.mappers;

import com.codewithmosh.store.dtos.ProductDto;
import com.codewithmosh.store.entities.Category;
import com.codewithmosh.store.entities.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(source= "category.id",target = "categoryId")
    ProductDto toDto(Product product);

//    @Mapping(source = "categoryId", target = "category")
    Product toProduct(ProductDto productDto);

    @Mapping(target = "id", ignore = true)
    void update(ProductDto productDto, @MappingTarget Product product);

//    default Category map(Byte categoryId) {
//        return categoryId == null ? null : new Category(categoryId);
//    }
}
