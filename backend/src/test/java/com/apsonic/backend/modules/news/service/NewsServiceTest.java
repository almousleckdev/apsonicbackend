package com.apsonic.backend.modules.news.service;

import com.apsonic.backend.modules.news.domain.News;
import com.apsonic.backend.modules.news.domain.NewsSection;
import com.apsonic.backend.modules.news.domain.SectionType;
import com.apsonic.backend.modules.news.dto.CreateNewsRequest;
import com.apsonic.backend.modules.news.dto.NewsDetailResponse;
import com.apsonic.backend.modules.news.dto.NewsResponse;
import com.apsonic.backend.modules.news.dto.NewsSectionDto;
import com.apsonic.backend.modules.news.mapper.NewsMapper;
import com.apsonic.backend.modules.news.repository.NewsRepository;
import com.apsonic.backend.modules.news.service.impl.NewsServiceImpl;
import com.apsonic.backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock
    private NewsRepository newsRepository;

    @Mock
    private NewsMapper newsMapper;

    @InjectMocks
    private NewsServiceImpl newsService;

    private News newsSample;

    @BeforeEach
    void setUp() {
        NewsSection section = NewsSection.builder()
                .id(1L)
                .sectionType(SectionType.HEADER)
                .headingLevel("H1")
                .contentEn("Test Header")
                .sectionOrder(0)
                .build();

        newsSample = News.builder()
                .slug("test-article")
                .titleEn("Test Article Title")
                .summaryEn("Test Summary")
                .category("Corporate")
                .authorName("APSONIC Team")
                .published(true)
                .viewsCount(5L)
                .build();
        newsSample.setId(1L);
        newsSample.addSection(section);
    }

    @Test
    @DisplayName("Should retrieve public news list with locale fallback")
    void getPublicNewsList_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<News> newsPage = new PageImpl<>(List.of(newsSample));
        when(newsRepository.findByPublishedTrueOrderByPublishedAtDesc(pageable)).thenReturn(newsPage);
        when(newsMapper.toNewsResponse(any(News.class), eq("en"))).thenReturn(
                NewsResponse.builder().id(1L).title("Test Article Title").slug("test-article").build()
        );

        Page<NewsResponse> result = newsService.getPublicNewsList("en", null, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Test Article Title");
    }

    @Test
    @DisplayName("Should retrieve news details by slug")
    void getNewsByIdOrSlug_Success() {
        when(newsRepository.findBySlugWithSections("test-article")).thenReturn(Optional.of(newsSample));
        when(newsMapper.toNewsDetailResponse(any(News.class), eq("en"))).thenReturn(
                NewsDetailResponse.builder().id(1L).slug("test-article").title("Test Article Title").viewsCount(6L).build()
        );

        NewsDetailResponse response = newsService.getNewsByIdOrSlug("test-article", "en");

        assertThat(response).isNotNull();
        assertThat(response.getSlug()).isEqualTo("test-article");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when news does not exist")
    void getNewsByIdOrSlug_NotFound() {
        when(newsRepository.findBySlugWithSections("unknown-slug")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newsService.getNewsByIdOrSlug("unknown-slug", "en"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("News article not found");
    }

    @Test
    @DisplayName("Should create news article with sections successfully")
    void createNews_Success() {
        CreateNewsRequest request = CreateNewsRequest.builder()
                .titleEn("New Release")
                .category("Product Launch")
                .sections(List.of(
                        NewsSectionDto.builder()
                                .sectionType(SectionType.PARAGRAPH)
                                .contentEn("Paragraph 1").build()
                ))
                .build();

        when(newsRepository.existsBySlug(anyString())).thenReturn(false);
        when(newsMapper.toSectionEntity(any(NewsSectionDto.class))).thenReturn(
                NewsSection.builder().sectionType(SectionType.PARAGRAPH).contentEn("Paragraph 1").build()
        );
        when(newsRepository.save(any(News.class))).thenAnswer(invocation -> {
            News saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });
        when(newsMapper.toNewsDetailResponse(any(News.class), eq("en"))).thenReturn(
                NewsDetailResponse.builder().id(2L).title("New Release").slug("new-release").build()
        );

        NewsDetailResponse response = newsService.createNews(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(2L);
        verify(newsRepository, times(1)).save(any(News.class));
    }
}
