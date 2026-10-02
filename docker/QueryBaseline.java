import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.flywaydb.core.Flyway;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import io.github.gflabandon.counselor.entity.*;
import io.github.gflabandon.counselor.mapper.*;
import io.github.gflabandon.counselor.service.*;
import io.github.gflabandon.counselor.web.*;
import tools.jackson.databind.json.JsonMapper;

/** Synthetic, single-client baseline against the packaged mappers in a disposable database. */
public final class QueryBaseline {
    static final String URL = "jdbc:mysql://db:3306/counselor?sslMode=DISABLED&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true";
    static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);
    static final int WARMUP = 3, REPEATS = 30, SIZE = 20;
    static Connection db;
    static Configuration config;
    static SqlSession session;
    static Counter counter = new Counter();
    static List<Map<String, Object>> results = new ArrayList<>();

    @Intercepts(@Signature(type=StatementHandler.class, method="prepare", args={Connection.class, Integer.class}))
    public static final class Counter implements Interceptor {
        int statements;
        public Object intercept(Invocation call) throws Throwable { statements++; return call.proceed(); }
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void sql(String sql) throws Exception { try (var s=db.createStatement()) { s.execute(sql); } }
    static String scalar(String sql) throws Exception {
        try (var s=db.createStatement(); var r=s.executeQuery(sql)) { r.next(); return r.getString(1); }
    }
    static String employee(int i) { return "SYN-" + String.format(Locale.ROOT, "%06d", i); }
    static String name(int i) { return "合成档案" + i; }
    static int department(int i) { return i % 10 + 1; }
    static EmploymentStatus status(int i) { return i % 5 == 0 ? EmploymentStatus.INACTIVE : EmploymentStatus.ACTIVE; }
    static String actor(int i) { return "synthetic_actor_" + i % 50; }
    static String outcome(int i) { return i % 4 == 0 ? "FAILURE" : "SUCCESS"; }
    static String target(int i, int n) { return Integer.toString(i % n + 1); }
    static LocalDateTime occurred(int i) { return START.plusDays(i % 60).plusSeconds(i % 86400); }

    static void seed(int n) throws Exception {
        db.setAutoCommit(true);
        sql("DELETE FROM audit_events"); sql("DELETE FROM counselors"); sql("DELETE FROM departments");
        for(int d=1;d<=10;d++) sql("INSERT INTO departments(id,name) VALUES ("+d+",'合成院系"+d+"')");
        db.setAutoCommit(false);
        try (var s=db.prepareStatement("INSERT INTO counselors(id,employee_no,name,department_id,employment_status) VALUES (?,?,?,?,?)")) {
            for(int i=1;i<=n;i++) {
                s.setInt(1,i);s.setString(2,employee(i));s.setString(3,name(i));s.setInt(4,department(i));s.setString(5,status(i).name());s.addBatch();
                if(i%1000==0) { s.executeBatch();db.commit(); }
            }
            s.executeBatch();db.commit();
        }
        try (var s=db.prepareStatement("INSERT INTO audit_events(id,actor,action,target_type,target_id,outcome,reason,occurred_at) VALUES (?,?,'COUNSELOR_UPDATE','COUNSELOR',?,?,'SYNTHETIC',?)")) {
            for(int i=1;i<=n*10;i++) {
                s.setInt(1,i);s.setString(2,actor(i));s.setString(3,target(i,n));s.setString(4,outcome(i));s.setTimestamp(5,Timestamp.valueOf(occurred(i)));s.addBatch();
                if(i%1000==0) { s.executeBatch();db.commit(); }
            }
            s.executeBatch();db.commit();
        }
        db.setAutoCommit(true);
        sql("ANALYZE TABLE counselors,audit_events,departments");
        db.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        db.setAutoCommit(false);
    }
    static List<Integer> expected(int limit, IntPredicate predicate, boolean descending) {
        var ids=new ArrayList<Integer>();
        for(int i=1;i<=limit;i++) if(predicate.test(i)) ids.add(i);
        if(descending) Collections.reverse(ids);
        return ids;
    }
    static Map<String,Object> params(Object... pairs) {
        var p=new HashMap<String,Object>();
        for(int i=0;i<pairs.length;i+=2) p.put((String)pairs[i],pairs[i+1]);
        return p;
    }
    static Map<String,Object> explain(Class<?> mapper, String method, Map<String,Object> p) throws Exception {
        var ms=config.getMappedStatement(mapper.getName()+"."+method);
        var bound=ms.getBoundSql(p);
        var handler=new DefaultParameterHandler(ms,p,bound);
        var plan=new ArrayList<String>();
        try(var s=db.prepareStatement("EXPLAIN ANALYZE "+bound.getSql())) {
            handler.setParameters(s);
            try(var r=s.executeQuery()) { while(r.next()) plan.add(r.getString(1)); }
        }
        var values=new ArrayList<Object>();
        var meta=config.newMetaObject(p);
        for(var mapping:bound.getParameterMappings()) values.add(Objects.toString(meta.getValue(mapping.getProperty()),null));
        return Map.of("sql",bound.getSql().replaceAll("\\s+"," ").trim(),"boundParameters",values,"explainAnalyze",plan);
    }
    record Page(long total, List<Integer> ids) {}
    static void measure(int n, String label, int requestedPage, List<Integer> expected, int expectedSql,
                        Supplier<Page> read, List<Map<String,Object>> plans, Map<String,Object> filters) throws Exception {
        int page=Math.max(1,Math.min(requestedPage,Math.max(1,(expected.size()+SIZE-1)/SIZE)));
        int offset=(page-1)*SIZE;
        var expectedPage=expected.subList(offset,Math.min(expected.size(),offset+SIZE));
        var timings=new ArrayList<Double>();
        for(int attempt=-WARMUP;attempt<REPEATS;attempt++) {
            session.clearCache(); counter.statements=0;
            long start=System.nanoTime();
            Page actual=read.get();
            double ms=(System.nanoTime()-start)/1_000_000.0;
            check(actual.total==expected.size() && actual.ids.equals(expectedPage),"Result mismatch: "+label);
            check(counter.statements==expectedSql,"SQL count mismatch: "+label+" = "+counter.statements);
            db.rollback();
            if(attempt>=0) timings.add(ms);
        }
        var sorted=new ArrayList<>(timings); Collections.sort(sorted);
        var result=new LinkedHashMap<String,Object>();
        result.put("counselors",n);result.put("auditEvents",n*10);result.put("scenario",label);result.put("filters",filters);
        result.put("page",page);result.put("pageSize",SIZE);result.put("totalMatches",expected.size());result.put("returnedIds",expectedPage);
        result.put("sqlPerSearch",expectedSql);result.put("medianMs",(sorted.get(14)+sorted.get(15))/2);
        result.put("p95Ms",sorted.get((int)Math.ceil(REPEATS*.95)-1));result.put("minMs",sorted.get(0));result.put("maxMs",sorted.get(REPEATS-1));
        result.put("samplesMs",timings);result.put("plans",plans);results.add(result);
        System.err.println("PASS "+n+" "+label+" median_ms="+result.get("medianMs"));
    }
    static void counselor(int n,String label,String keyword,Integer department,EmploymentStatus status,int page) throws Exception {
        var ids=expected(n,i->(keyword.isEmpty() || employee(i).toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)) || name(i).contains(keyword))
                && (department==null || department(i)==department) && (status==null || status(i)==status),false);
        int actualPage=Math.max(1,Math.min(page,Math.max(1,(ids.size()+SIZE-1)/SIZE)));
        var p=params("keyword",keyword,"departmentId",department,"status",status,"size",SIZE,"offset",(long)(actualPage-1)*SIZE);
        var service=new CounselorService(session.getMapper(CounselorMapper.class),null,null,null);
        var plans=List.of(explain(CounselorMapper.class,"count",p),explain(CounselorMapper.class,"findPage",p));
        measure(n,label,page,ids,2,()-> {
            var r=service.search(keyword,department,status,page,SIZE);
            return new Page(r.total(),r.items().stream().map(Counselor::getId).toList());
        },plans,params("keyword",keyword,"departmentId",department,"status",status==null?null:status.name()));
    }
    static void audit(int n,String label,AuditQuery q) throws Exception {
        var f=q.filter();
        var ids=expected(n*10,i->(f.actor().isEmpty() || actor(i).equalsIgnoreCase(f.actor()))
                && (f.targetType().isEmpty() || f.targetType().equals("COUNSELOR"))
                && (f.targetId().isEmpty() || target(i,n).equals(f.targetId()))
                && (f.outcome().isEmpty() || outcome(i).equals(f.outcome()))
                && (f.from()==null || !occurred(i).isBefore(f.from())) && (f.until()==null || occurred(i).isBefore(f.until())),true);
        int page=Math.max(1,Math.min(q.getPage(),Math.max(1,(ids.size()+SIZE-1)/SIZE)));
        var p=params("filter",f,"size",SIZE,"offset",(long)(page-1)*SIZE);
        var plans=List.of(explain(AuditMapper.class,"count",p),explain(AuditMapper.class,"findPage",p),explain(AuditMapper.class,"timeZone",p));
        var service=new AuditService(session.getMapper(AuditMapper.class));
        measure(n,label,q.getPage(),ids,3,()-> {
            var r=service.search(q).page();
            return new Page(r.total(),r.items().stream().map(a->Math.toIntExact(a.getId())).toList());
        },plans,params("actor",q.getActor(),"targetType",q.getTargetType(),"targetId",q.getTargetId(),"outcome",q.getOutcome(),"start",q.getStartDate(),"end",q.getEndDate()));
    }
    public static void main(String[] args) throws Exception {
        var ds=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",URL,"counselor_app",System.getenv("DB_PASSWORD"));
        try(var connection=ds.getConnection()) {
            db=connection;
            check(scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()").equals("0"),"Refuse nonempty database");
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true).load().migrate();
            config=new Configuration(new Environment("baseline",new JdbcTransactionFactory(),ds));
            config.setDatabaseId("MySQL");config.setMapUnderscoreToCamelCase(true);config.setLocalCacheScope(LocalCacheScope.STATEMENT);
            config.addInterceptor(counter);config.addMapper(CounselorMapper.class);config.addMapper(AuditMapper.class);
            try(var s=new SqlSessionFactoryBuilder().build(config).openSession(db)) {
                session=s;
                for(int n:new int[]{100,1000,10000}) {
                    seed(n);
                    counselor(n,"counselor_first","",null,null,1);
                    counselor(n,"counselor_deep","",null,null,n/SIZE);
                    counselor(n,"counselor_department_status","",2,EmploymentStatus.ACTIVE,1);
                    counselor(n,"counselor_substring","001",null,null,1);
                    counselor(n,"counselor_empty","no-synthetic-match",null,null,1);
                    audit(n,"audit_first",new AuditQuery());
                    var deep=new AuditQuery();deep.setPage(n*10/SIZE);audit(n,"audit_deep",deep);
                    var actor=new AuditQuery();actor.setActor("SYNTHETIC_ACTOR_7");audit(n,"audit_actor",actor);
                    var combination=new AuditQuery();combination.setTargetType("COUNSELOR");combination.setOutcome("FAILURE");combination.setStartDate("2026-01-10");combination.setEndDate("2026-01-20");audit(n,"audit_date_outcome",combination);
                    var exact=new AuditQuery();exact.setTargetType("COUNSELOR");exact.setTargetId("11");audit(n,"audit_target",exact);
                    var empty=new AuditQuery();empty.setActor("absent_actor");audit(n,"audit_empty",empty);
                }
                var report=new LinkedHashMap<String,Object>();
                report.put("passed",true);report.put("mysqlVersion",scalar("SELECT VERSION()"));report.put("dbTimeZone",scalar("SELECT @@session.time_zone"));
                report.put("collation",scalar("SELECT @@collation_database"));report.put("bufferPoolBytes",scalar("SELECT @@innodb_buffer_pool_size"));
                report.put("javaVersion",System.getProperty("java.version"));report.put("warmups",WARMUP);report.put("repeats",REPEATS);
                report.put("timingScope","service search + actual MyBatis/JDBC mapping on one existing connection; excludes connection acquisition, rollback, HTTP, authentication and templates; warm cache; sequential client");
                report.put("results",results);
                System.out.println("BASELINE_JSON="+JsonMapper.builder().build().writeValueAsString(report));
            }
        }
    }
}
