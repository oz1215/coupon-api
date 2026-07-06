package jp.co.sugipharmacy.coupon

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * モジュール境界の強制（ガードレール）。
 *
 * 1. eligibility 側（domain / application / infrastructure / interfaces）は
 *    member-coupon-state モジュール（`member` パッケージ）に依存してはならない。
 *    依存方向は member → eligibility の一方向のみ。
 * 2. ドメイン層（eligibility・member とも）は Spring・上位レイヤに依存しない純 Kotlin。
 */
@AnalyzeClasses(
    packages = ["jp.co.sugipharmacy.coupon"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class ArchitectureTest {

    @ArchTest
    val eligibilitySideMustNotDependOnMemberModule: ArchRule =
        noClasses()
            .that().resideOutsideOfPackage("jp.co.sugipharmacy.coupon.member..")
            .should().dependOnClassesThat().resideInAPackage("jp.co.sugipharmacy.coupon.member..")
            .because("依存方向は member → eligibility の一方向のみ（eligibility エンジンは member-agnostic を維持する）")

    @ArchTest
    val domainMustBePureKotlin: ArchRule =
        noClasses()
            .that().resideInAnyPackage(
                "jp.co.sugipharmacy.coupon.domain..",
                "jp.co.sugipharmacy.coupon.member.domain..",
            )
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.servlet..",
                "..application..",
                "..infrastructure..",
                "..interfaces..",
            )
            .because("ドメインは Spring・HTTP・上位レイヤを知らない純 Kotlin に保つ")
}
