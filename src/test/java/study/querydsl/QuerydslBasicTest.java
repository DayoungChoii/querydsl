package study.querydsl;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @PersistenceContext
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {

        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");

        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL() {

        String qlString =
                "select m from Member m " +
                "where m.username = :username";

        Member foundMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(foundMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl() {

        Member foundMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assertThat(foundMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search() {
        Member foundMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10)))
                .fetchOne();

        assertThat(foundMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search2() {
        Member foundMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"),
                        member.age.eq(10))
                .fetchOne();

        assertThat(foundMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchResult() {
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

/*        Member member = queryFactory
                .selectFrom(QMember.member)
                .fetchOne();*/

        Member member1 = queryFactory
                .selectFrom(QMember.member)
                .fetchFirst();
    }

    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        assertThat(result.get(0).getUsername()).isEqualTo("member5");
        assertThat(result.get(1).getUsername()).isEqualTo("member6");
        assertThat(result.get(2).getUsername()).isNull();
    }

    @Test
    public void paging() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void aggregation() {
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);

        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    @Test
    public void group() {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);




    }

    @Test
    public void join() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();



        assertThat(result)
                .extracting("username")
                .contains("member1", "member2");
    }

    @Test
    public void join_on_filterling() throws Exception {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void join_on_no_relation() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Tuple> fetch = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : fetch) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;
    @Test
    public void fetchJoinUser() throws Exception {
        em.flush();
        em.clear();

        Member foundMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(foundMember.getTeam());
        assertThat(loaded).as("페치 조인 적용").isTrue();
    }

    @Test
    public void subQuery() throws Exception{
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory.selectFrom(memberSub)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();
        assertThat(result).extracting("age")
                .contains(40);
    }

    @Test
    public void subQueryGoe() throws Exception {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age").contains(30, 40);
    }

    @Test
    public void simpleCase() throws Exception {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void buildCase() throws Exception{
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20")
                        .when(member.age.between(21, 30)).then("21~30")
                        .otherwise("etc")
                )
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);

        }

    }

    @Test
    public void constant() throws Exception {
        Tuple result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetchFirst();

        System.out.println(result);
    }

    @Test
    public void concat() throws Exception{
        //given
        List<String> fetch = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("memberA"))
                .fetch();
        //when
        for (String s : fetch) {
            System.out.println("s = " + s);
        }

        //then

    }
    
    @Test
    public void simpleProjection() throws Exception{
        //given
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        //when
        for (String s : result) {
            System.out.println("s = " + s);
        }
        //then
    
    }

    @Test
    public void tupleProjection() throws Exception{
        //given
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        //when
        for (Tuple tuple : result) {
            System.out.println(tuple.get(member.username) + " : " + tuple.get(member.age));
        }

        //then

    }

    @Test
    public void findDtoByJPQL() throws Exception{
        //given
        List<MemberDto> resultList = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();
        //when
        for (MemberDto memberDto : resultList) {
            System.out.println("memberDto = " + memberDto);
        }

        //then

    }
    
    @Test
    public void findDtoBySetter() throws Exception{
        //given
        List<MemberDto> result = queryFactory.select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
        //when
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
        //then
    
    }

    @Test
    public void findDtoByField() throws Exception{
        //given
        List<MemberDto> result = queryFactory.select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
        //when
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
        //then

    }

    @Test
    public void findDtoByConstruct() throws Exception{
        //given
        List<MemberDto> result = queryFactory.select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
        //when
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
        //then

    }

    @Test
    public void findDtoByQueryProjection() throws Exception{
        List<MemberDto> result = queryFactory.select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }


}
