using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Gastapp.Models;
using Gastapp.Models.Models;
using Refit;

namespace Gastapp.Services.ApiService
{
    public interface IApiService
    {

        [Post("/User/CreateUser")]
        public Task<string> CreateUser(User user);


        [Post("/Spendings/SyncNewSpendings")]
        public Task<bool> SyncNewSpendings(List<SpendingDto> spendings);

        [Post("/Spendings/SyncNewCategories")]
        public Task<bool> SyncNewCategories(List<CategoryDto> spendings);

        [Post("/User/Login")]
        public Task<AllUserData> Login(LoginModel login);

        [Get("/Spendings/GetIncomes")]
        public Task<List<IncomeType>> GetIncomes();

        [Post("/Spendings/CreateNewSpending")]
        public Task<bool> CreateNewSpending(NewSpendingDto spending);

        [Post("/Spendings/CreateNewCategory")]
        public Task<bool> CreateNewCategory(CategoryDto spending);
    }
}
