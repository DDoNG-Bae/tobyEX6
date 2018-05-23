package com.dasom.ex.user.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.suppliers.TestedOnSupplier;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;

import com.dasom.ex.mail.MailSender;
import com.dasom.ex.mail.MockMailSender;
import com.dasom.ex.user.dao.UserDao;
import com.dasom.ex.user.domain.Level;
import com.dasom.ex.user.domain.User;

import static com.dasom.ex.user.service.UserService.MIN_LOGCOUNT_FOR_SILVER;
import static com.dasom.ex.user.service.UserService.MIN_RECCOMEND_FOR_GOLD;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations="/applicationContext.xml")
public class UserServiceTest {
	@Autowired UserService userService;
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
		
		userService.upgradeLevels();
		
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
		
		userService.add(userWithLevel);
		userService.add(userWithoutLevel);
	
		User userWithLevelRead = userDao.get(userWithLevel.getId());
		User userWithoutLevelRead = userDao.get(userWithoutLevel.getId());
		
		assertThat(userWithLevelRead.getLevel(),is(userWithLevel.getLevel()));
		assertThat(userWithoutLevelRead.getLevel(),is(Level.BASIC));
	}
	
	@Test
	public void upgradeAllOrNothing() throws Exception{
		UserService testUserService = new TestUserService(users.get(3).getId());
		testUserService.setUserDao(this.userDao);
		testUserService.setTransactionManager(transactionManager);
		testUserService.setMailSender(mailSender);
		
		userDao.deleteAll();
		for(User user:users) userDao.add(user);
		
		try {
			testUserService.upgradeLevels();
			fail("TestUserServiceException expected");
		}
		catch(TestUserServiceException e) {
			
		}
		
		checkLevel(users.get(1), false);
	}
	
	static class TestUserService extends UserService{
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

	@Test
	@DirtiesContext
	public void upgradeLevels() throws Exception{
		userDao.deleteAll();
		for(User user:users) userDao.add(user);
		
		MockMailSender mockMailSender = new MockMailSender();
		userService.setMailSender(mockMailSender);
		
		userService.upgradeLevels();
		checkLevel(users.get(0), false);
		checkLevel(users.get(1), true);
		checkLevel(users.get(2), false);
		checkLevel(users.get(3), true);
		checkLevel(users.get(4), false);
		
		List<String> requests = mockMailSender.getRequests();
		assertThat(requests.size(),is(2));
		assertThat(requests.get(0), is(users.get(1).getEmail()));
		assertThat(requests.get(1), is(users.get(3).getEmail()));
	}
}
