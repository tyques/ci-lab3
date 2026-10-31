package lab.shift.crmsystemforshift.services.impl;

import lab.shift.crmsystemforshift.data.dto.requests.SellerCreateRequest;
import lab.shift.crmsystemforshift.data.dto.requests.SellerUpdateRequest;
import lab.shift.crmsystemforshift.data.dto.response.SellerResponse;
import lab.shift.crmsystemforshift.data.entities.SellerEntity;
import lab.shift.crmsystemforshift.exceptions.ResourceNotFoundException;
import lab.shift.crmsystemforshift.mappers.Mapper;
import lab.shift.crmsystemforshift.repositories.SellerRepository;
import lab.shift.crmsystemforshift.services.SellerService;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class SellerServiceImpl implements SellerService {
    private final SellerRepository sellerRepository;
    private final Mapper <SellerEntity, SellerResponse> mapper;

    @Override
    @Transactional(readOnly = true)
    public List<SellerResponse> getAllSellers() {
        return sellerRepository.findAll().stream()
                .map(mapper::mapToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SellerResponse> getAllSellersWithDeleted() {
        return sellerRepository.findAllWithDeleted().stream()
                .map(mapper::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "sellers", key = "#id")
    public SellerResponse getSellerById(Long id) {
        return sellerRepository.findById(id)
                .map(mapper::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Seller with id " + id + " not found"));
    }

    @Override
    @Transactional
    public SellerResponse createSeller(SellerCreateRequest request) {
        SellerEntity seller = SellerEntity.builder()
                .name(request.name())
                .contactInfo(request.contactInfo())
                .build();
        SellerEntity savedSeller = sellerRepository.save(seller);
        return mapper.mapToDto(savedSeller);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "sellers", key = "#id")
    public SellerResponse updateSeller(Long id, SellerUpdateRequest request) {
        SellerEntity sellerToUpdate = sellerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seller with id " + id + " not found"));

        sellerToUpdate.setName(request.name());
        sellerToUpdate.setContactInfo(request.contactInfo());

        SellerEntity updatedSeller = sellerRepository.save(sellerToUpdate);
        return mapper.mapToDto(updatedSeller);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "sellers", key = "#id")
    public void deleteSeller(Long id) {
        if (!sellerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Seller with id " + id + " not found");
        }
        sellerRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public SellerResponse getMostProductiveSeller(LocalDateTime start, LocalDateTime end) {
        return sellerRepository.findMostProductiveSeller(start, end)
                .map(mapper::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("No productive seller found in the given period"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SellerResponse> getSellersBelowAmount(LocalDateTime start, LocalDateTime end, double amount) {
        return sellerRepository.findSellersWithTotalAmountLessThan(start, end, amount).stream()
                .map(mapper::mapToDto)
                .collect(Collectors.toList());
    }
}