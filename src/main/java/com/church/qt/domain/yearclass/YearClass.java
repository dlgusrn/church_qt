package com.church.qt.domain.yearclass;

import com.church.qt.common.BaseTimeEntity;
import com.church.qt.domain.year.Year;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "year_classes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YearClass extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "year_id", nullable = false)
    private Year year;

    @Column(name = "class_name", nullable = false, length = 50)
    private String className;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Builder
    public YearClass(
            Year year,
            String className,
            Integer sortOrder,
            Boolean active
    ) {
        this.year = year;
        this.className = className;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public void updateInfo(
            String className,
            Integer sortOrder,
            Boolean active
    ) {
        this.className = className;
        this.sortOrder = sortOrder;
        this.active = active;
    }
}