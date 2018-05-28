package com.dasom.ex.user.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Checksum;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.suppliers.TestedOnSupplier;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.verification.Times;
import org.springframework.aop.ThrowsAdvice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import com.dasom.ex.user.dao.UserDao;
import com.dasom.ex.user.domain.Level;
import com.dasom.ex.user.domain.User;
import com.dasom.ex.user.mail.MailSender;
import com.dasom.ex.user.mail.MockMailSender;

import static com.dasom.ex.user.service.UserServiceImpl.MIN_LOGCOUNT_FOR_SILVER;
import static com.dasom.ex.user.service.UserServiceImpl.MIN_RECCOMEND_FOR_GOLD;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations="/applicationContext.xml")
public class UserServiceTest {
	@Autowired UserServiceImpl userServiceImpl;
	@Autowired UserDao userDao; 
	@Autowired DataSource dataSource;
	@Autowired MailSender mailSender;
	@Autowired PlatformTransactionManager transactionManager;
	
	List<User> users;
	@Before
	public void setUp() {
		users=Arrays.asList(
				new User("t1","ㅌ1","p1",Level.BASIC,MIN_LOGCOUNT_FOR_SILVER-1,0,"ektha03@gmail.com"),
				new User("t2","ㅌ2","p2",Level.BASIC,MIN_LOGCOUNT_FOR_SILVER,0,"ektha03@gmail.com"),
				new User("t3","ㅌ3","p3",Level.SILVER,MIN_RECCOMEND_FOR_GOLD-1,29,"ektha03@gmail.com"),
				new User("t4","ㅌ4","p4",Level.SILVER,MIN_RECCOMEND_FOR_GOLD,30,"ektha03@gmail.com"),
				new User("t5","ㅌ5","p5",Level.GOLD,100,Integer.MAX_VALUE,"ektha03@gmail.com")
				);
	}
	
	@Test
	public void upgradeLevel() throws Exception{
		userDao.deleteAll();
		
		for(User user:users)
			userDao.add(user);
		
		userServiceImpl.upgradeLevels();
		
		checkLevel(users.get(0), false);
		checkLevel(users.get(1), true);
		checkLevel(users.get(2), false);
		checkLevel(users.get(3), true);
		checkLevel(users.get(4), false);
	}
	
	public void checkLevel(User user,boolean upgraded) {
		User userUpdate = userDao.get(user.getId());
		if(upgraded) {
			assertThat(userUpdate.getLevel(),is(user.getLevel().nextLevel()));
		}
		else {
			assertThat(userUpdate.getLevel(),is(user.getLevel()));
		}
	}
	
	@Test
	public void add() {
		userDao.deleteAll();
		
		User userWithLevel = users.get(4);
		User userWithoutLevel = users.get(0);
		userWithoutLevel.setLevel(null);
		
		userServiceImpl.add(userWithLevel);
		userServiceImpl.add(userWithoutLevel);
	
		User userWithLevelRead = userDao.get(userWithLevel.getId());
		User userWithoutLevelRead = userDao.get(userWithoutLevel.getId());
		
		assertThat(userWithLevelRead.getLevel(),is(userWithLevel.getLevel()));
		assertThat(userWithoutLevelRead.getLevel(),is(Level.BASIC));
	}
	
	@Test
	public void upgradeAllOrNothing() throws Exception{
		UserServiceImpl testUserService = new TestUserService(users.get(3).getId());
		testUserService.setUserDao(this.userDao);
		testUserService.setMailSender(mailSender);
		
		TransactionHandler txHandler = new TransactionHandler();
		txHandler.setTarget(testUserService);
		txHandler.setTransactionManager(transactionManager);
		txHandler.setPattern("upgradeLevels");
		
		UserService txUserService = (UserService)Proxy.newProxyInstance(
				getClass().getClassLoader(),new Class[] {UserService.class}, txHandler);
		
		userDao.deleteAll();
		for(User user:users) userDao.add(user);
		
		try {
			txUserService.upgradeLevels();
			fail("TestUserServiceException expected");
		}
		catch(TestUserServiceException e) {
			
		}
		
		checkLevel(users.get(1), false);
	}
	
	
	@Test
	public void upgradeLevels() throws Exception{
		UserServiceImpl userServiceImpl = new UserServiceImpl();//고립된 테스트에서는 테스트 대상 오브젝트를 직접 생성하면 된다.
		
		MockUserDao mockUserDao = new MockUserDao(this.users);//목 오브젝트로 만든 UserDao를 직접 DI해준다.
		userServiceImpl.setUserDao(mockUserDao);
		
		MockMailSender mockMailSender = new MockMailSender();
		userServiceImpl.setMailSender(mockMailSender);
		
		userServiceImpl.upgradeLevels();
		
		List<User> updated = mockUserDao.getUpdated();//업테이트 결과를 가져온다.
		
		assertThat(updated.size(),is(2));
		checkUserAndLevel(updated.get(0),"t2", Level.SILVER);
		checkUserAndLevel(updated.get(1),"t4",Level.GOLD);
		
		List<String> requests = mockMailSender.getRequests();
		assertThat(requests.size(),is(2));
		assertThat(requests.get(0), is(users.get(1).getEmail()));
		assertThat(requests.get(1), is(users.get(3).getEmail()));
	}
	
	//Mockito 프레임워크를 이용한 테스트
	@Test
	public void mockUpgradeLevels() throws Exception {
		UserServiceImpl userServiceImpl = new UserServiceImpl();
		
		//목 오브젝트생성,메소드 리턴값 설정, DI 한번에
		UserDao mockUserDao = mock(UserDao.class);
		when(mockUserDao.getAll()).thenReturn(this.users);
		userServiceImpl.setUserDao(mockUserDao);
		//
		
		//리턴 값 없는 메소드
		MailSender mockMailSender = mock(MailSender.class);
		userServiceImpl.setMailSender(mockMailSender);
		//
		
		userServiceImpl.upgradeLevels();
		
		//목 오브젝트가 제공하는 검증 기능을 통해서 확인
		verify(mockUserDao,times(2)).update(any(User.class));
		verify(mockUserDao,times(2)).update(any(User.class));
		verify(mockUserDao).update(users.get(1));
		assertThat(users.get(1).getLevel(),is(Level.SILVER));
		verify(mockUserDao).update(users.get(3));
		assertThat(users.get(3).getLevel(), is(Level.GOLD));
		
		ArgumentCaptor<SimpleMailMessage> mailMessageArg = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mockMailSender,times(2)).send(mailMessageArg.capture());//파라미터를 캡쳐할 수 있다
		List<SimpleMailMessage> mailMessages = mailMessageArg.getAllValues();
		assertThat(mailMessages.get(0).getTo()[0],is(users.get(1).getEmail()));
		assertThat(mailMessages.get(1).getTo()[0],is(users.get(3).getEmail()));
	}
	
	private void checkUserAndLevel(User updated, String expectedId, Level expectedLevel) {
		assertThat(updated.getId(),is(expectedId));
		assertThat(updated.getLevel(),is(expectedLevel));
	}
	
	static class TestUserService extends UserServiceImpl{
		private String id;
		private TestUserService(String id) {
			this.id=id;
		}
		
		@Override
		protected void upgradeLevel(User user) {
			if(user.getId().equals(this.id)) throw new TestUserServiceException();
			super.upgradeLevel(user);
		}
	}
	
	static class TestUserServiceException extends RuntimeException {
	}

	//getAll()에서는 스텁으로 update()에서는 목 오브젝트로서 동작하는 UserDao 타입의 테스트 대역
	static class MockUserDao implements UserDao{
		private List<User> users;
		private List<User> updated = new ArrayList<User>();
		
		
		
		private MockUserDao(List<User> users) {
			this.users = users;
		}
		
		public List<User> getUpdated(){
			return this.updated;
		}
		
		//스텁 기능 제공
		public List<User> getAll() {return this.users;}
		
		//목 오브젝트 기능 제공
		public void update(User user) {updated.add(user);}
		
		
		//테스트에 사용되지 않는 메소드
		public void add(User user) {throw new UnsupportedOperationException();}
		public User get(String id) {throw new UnsupportedOperationException();}
		public void deleteAll() {throw new UnsupportedOperationException();}
		public int getCount() {throw new UnsupportedOperationException();}
		//
	}
	
	static class TransactionHandler implements InvocationHandler{
		private Object target;	//타깃 오브젝트
		private PlatformTransactionManager transactionManager;//트랜젝션 기능 제공 위한 트랜젝션 매니저
		private String pattern;	//트랜잭션 적용할 메소드 이름 패턴
		
		public void setTarget(Object target) {
			this.target = target;
		}

		public void setTransactionManager(PlatformTransactionManager transactionManager) {
			this.transactionManager = transactionManager;
		}

		public void setPattern(String pattern) {
			this.pattern = pattern;
		}

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if(method.getName().startsWith(pattern)) {
				return invokeInTransaction(method, args);
			}
			return method.invoke(target, args);
		}
		
		private Object invokeInTransaction(Method method, Object[] args) throws Throwable{
			TransactionStatus status =
					this.transactionManager.getTransaction(new DefaultTransactionDefinition());
			try {
				Object ret = method.invoke(target, args);
				this.transactionManager.commit(status);
				return ret;
			}catch(InvocationTargetException e) {
				this.transactionManager.rollback(status);
				throw e.getTargetException();
			}
		}
		
	}
}
