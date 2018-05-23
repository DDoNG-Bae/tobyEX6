package com.dasom.ex.user.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import com.dasom.ex.user.domain.Level;
import com.dasom.ex.user.domain.User;


public class UserDaoJdbc implements UserDao{
	private JdbcTemplate jdbcTemplate;
	private RowMapper<User> userMapper = new RowMapper<User>() {
		
		public User mapRow(ResultSet rs, int rowNum) throws SQLException {
			User user = new User();
			user.setId(rs.getString("id"));
			user.setName(rs.getString("name"));
			user.setPassword(rs.getString("password"));
			user.setLevel(Level.valueOf(rs.getInt("user_level")));//오라클에서 level은 예약어라서 컴럼명으로 사용불가
			user.setLogin(rs.getInt("login"));
			user.setRecommend(rs.getInt("recommend"));
			user.setEmail(rs.getString("email"));
			return user;
		}
	};
	public UserDaoJdbc() {};
	
	public void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}
	
	
	public void deleteAll(){
		this.jdbcTemplate.update("delete from users");
	}
	
	//Spring 3.2부터 queryforInt/queryforLong 메소드 사용불가
	//queryForObject로 대체
	public int getCount(){
		return this.jdbcTemplate.queryForObject("select count(*) from users",Integer.class);
	}
	
	public void add(final User user){	
		this.jdbcTemplate.update("insert into users(id,name,password,user_level,login,recommend,email) values(?,?,?,?,?,?,?)",
				user.getId(),user.getName(),user.getPassword(),user.getLevel().intValue(),user.getLogin(),user.getRecommend(),user.getEmail());
	}
	
	public User get(String id){
		return this.jdbcTemplate.queryForObject("select * from users where id = ?", new Object[] {id}, userMapper);
	}
	
	public List<User> getAll(){
		return this.jdbcTemplate.query("select * from users order by id", userMapper);
	}
	
	public void update(User user) {
		this.jdbcTemplate.update("update users set name=?,password=?,user_level=?,login=?,recommend=?,email=? where id=?",
				user.getName(), user.getPassword(), user.getLevel().intValue(), user.getLogin(), user.getRecommend(),user.getEmail(), user.getId());
	}
}
