package com.example.beyond.ordersystem.product.service;

import com.example.beyond.ordersystem.common.service.StockInventoryService;
import com.example.beyond.ordersystem.product.domain.Product;
import com.example.beyond.ordersystem.product.dto.*;
import com.example.beyond.ordersystem.product.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import javax.persistence.EntityNotFoundException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository productRepository;
    private final S3Client s3Client;
    private final StockInventoryService stockInventoryService;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Autowired
    public ProductService(ProductRepository productRepository, S3Client s3Client, StockInventoryService stockInventoryService) {
        this.productRepository = productRepository;
        this.s3Client = s3Client;
        this.stockInventoryService = stockInventoryService;
    }


    // 방법1. local pc에 임시 저장 - file.write
    @Transactional
    public Product productCreate(ProductSaveDto dto) {
        MultipartFile image = dto.getProductImage();
        Product product = null;
        try {
            product = productRepository.save(dto.toEntity());
            byte[] bytes = image.getBytes();
            Path path = Paths.get("/Users/milcho/etc/tmp/",
                    product.getId() + "_" + image.getOriginalFilename());
            Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            product.updateImagePath(path.toString());

            if (dto.getName().contains("sale")){
                stockInventoryService.increaseStock(product.getId(), dto.getStock_quantity());
            }
            // 위는 dirtyChecking 과정을 거쳐 변경을 감지한다. -> 다시 save 할 필요가 없음. !!
        } catch (IOException e) {
            throw new RuntimeException("이미지 저장에 실패했습니다.");
        }
        return product;
    }


    // 방법2 : aws 에 pc에 저장된 파일을 업로드
    @Transactional
    public Product productAwsCreate(ProductSaveDto dto) {
        MultipartFile image = dto.getProductImage();
        Product product = null;
        try {
            product = productRepository.save(dto.toEntity());
            byte[] bytes = image.getBytes();
            String filName = product.getId() + "_" + image.getOriginalFilename();
            Path path = Paths.get("/Users/milcho/etc/tmp/", filName);
            Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(filName)
                    .build();
            PutObjectResponse putObjectResponse = s3Client.putObject(putObjectRequest,
                    RequestBody.fromFile(path)
                    );
            String s3Path = s3Client.utilities().getUrl(a -> a.bucket(bucket).key(filName)).toExternalForm();
            // 위는 dirtyChecking 과정을 거쳐 변경을 감지한다. -> 다시 save 할 필요가 없음. !!
            product.updateImagePath(s3Path);
        } catch (IOException e) {
            throw new RuntimeException("이미지 저장에 실패했습니다.");
        }
        return product;
    }

    @Transactional
    public Page<ProductResDto> productList(ProductSearchDto searchDto, Pageable pageable) {
        // 검색을 위해 Specification 객체를 사용
        // Specification 객체는 복잡한 쿼리를 명세를 이용하여 정의하는 방식으로, 쿼리를 쉽게 생성
        Specification<Product> specification = new Specification<Product>() {
            @Override
            public Predicate toPredicate(Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                List<Predicate> predicateList = new ArrayList<>();
                if (searchDto.getSearchName() != null){
                    // root: 엔티티의 속성을 접근하기 위한 객체, CriteriaBuilder : 쿼리를 생성하기 위한 객
                    predicateList.add(criteriaBuilder.like(root.get("name"), "%" + searchDto.getSearchName() + "%"));
                }
                if (searchDto.getCategory() != null){
                    predicateList.add(criteriaBuilder.like(root.get("category"), "%" + searchDto.getCategory() + "%"));
                }
                Predicate[] predicateArr = new Predicate[predicateList.size()];
                for (int i = 0; i < predicateArr.length; i++) {
                    predicateArr[i] = predicateList.get(i);
                }
                Predicate predicate = criteriaBuilder.and(predicateArr);
                return predicate;
            }
        };
        Page<Product> products = productRepository.findAll(specification, pageable);
        return products.map(a -> a.fromEntity());
    }

    public ProductResDto productDetail (Long id){
        return productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 상품입니다."))
                .fromEntity();
    }

    @Transactional
    public Product productUpdateStock(ProductUpdateStockDto dto){
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 상품입니다."));
        product.UpdatStockQuantity(dto.getProductQuantity());
        return product;
    }

}
